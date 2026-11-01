package com.wanfo.bi.service;

import com.wanfo.bi.mapper.AccessRequestMapper;
import com.wanfo.bi.mapper.BiResourceMapper;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class AccessRequestService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccessRequestService.class);
    private static final Set<String> RESOURCE_TYPES = Set.of("dataset", "metric", "datasource", "dashboard", "chart", "data_api");

    private final AccessRequestMapper mapper;
    private final BiResourceAuthorizer resources;
    private final BiResourceMapper resourceMapper;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final CatalogAuditService audit;
    private final ObjectMapper json;

    public AccessRequestService(AccessRequestMapper mapper, BiResourceAuthorizer resources, BiResourceMapper resourceMapper,
            CapabilityProvider capabilities, CurrentActorProvider actors, CatalogAuditService audit, ObjectMapper json) {
        this.mapper = mapper;
        this.resources = resources;
        this.resourceMapper = resourceMapper;
        this.capabilities = capabilities;
        this.actors = actors;
        this.audit = audit;
        this.json = json;
    }

    public List<Map<String, Object>> mine() {
        return mapper.listMine(actors.currentActor().userId()).stream().map(this::decode).toList();
    }

    public List<Map<String, Object>> pending() {
        return mapper.listPending().stream().filter(this::canApprove).map(this::decode).toList();
    }

    public Map<String, Object> get(Long id) {
        Map<String, Object> row = mapper.find(id);
        if (row == null) throw new IllegalArgumentException("访问申请不存在");
        Long requester = number(row.get("requesterUserId"));
        if (!actors.currentActor().userId().equals(requester) && !canApprove(row))
            throw new com.wanfo.bi.security.BiAccessException(403, "无权查看该申请");
        return decode(row);
    }

    @Transactional
    public Map<String, Object> create(CreateRequest request) {
        if (request == null) throw new IllegalArgumentException("请求不能为空");
        String type = request.resourceType() == null ? "" : request.resourceType().trim().toLowerCase(Locale.ROOT);
        if (!RESOURCE_TYPES.contains(type)) throw new IllegalArgumentException("资源类型不支持申请");
        if (request.resourceId() == null || request.resourceId() < 1) throw new IllegalArgumentException("请指定资源");
        String purpose = request.purpose() == null ? "" : request.purpose().trim();
        if (purpose.isBlank() || purpose.length() > 500) throw new IllegalArgumentException("申请目的须为 1～500 字");
        List<String> actions = request.actions() == null ? List.of() : request.actions().stream()
                .map(a -> a == null ? "" : a.trim().toLowerCase(Locale.ROOT)).filter(a -> !a.isBlank()).distinct().toList();
        if (actions.isEmpty() || actions.size() > 8) throw new IllegalArgumentException("请选择 1～8 个申请动作");
        int days = request.validDays() == null ? 30 : request.validDays();
        if (days < 1 || days > 365) throw new IllegalArgumentException("有效期须为 1～365 天");
        Map<String, Object> row = new HashMap<>();
        row.put("purpose", purpose);
        row.put("resourceType", type);
        row.put("resourceId", request.resourceId());
        row.put("assetUrn", blank(request.assetUrn(), 1000));
        try { row.put("actionsJson", json.writeValueAsString(actions)); }
        catch (Exception e) { throw new IllegalStateException("动作序列化失败", e); }
        row.put("validDays", days);
        row.put("requestedBy", actors.currentActor().username());
        row.put("requesterUserId", actors.currentActor().userId());
        if (mapper.insert(row) != 1 || !(row.get("id") instanceof Number id))
            throw new IllegalStateException("访问申请创建失败");
        audit.record("ACCESS_REQUEST_CREATE", type.toUpperCase(Locale.ROOT), request.resourceId(), null,
                Map.of("requestId", id.longValue(), "actions", actions.size()));
        return get(id.longValue());
    }

    @Transactional
    public Map<String, Object> decide(Long id, boolean approve, String note) {
        Map<String, Object> row = mapper.find(id);
        if (row == null) throw new IllegalArgumentException("访问申请不存在");
        if (!"PENDING".equals(String.valueOf(row.get("status")))) throw new IllegalArgumentException("申请已处理");
        requireApproverFor(row);
        String clean = note == null ? null : note.trim();
        if (clean != null && clean.length() > 1000) throw new IllegalArgumentException("审批意见过长");
        LocalDateTime expiresAt = null;
        if (approve) {
            int days = number(row.get("validDays")).intValue();
            expiresAt = LocalDateTime.now().plusDays(days);
            Long principal = number(row.get("requesterUserId"));
            String type = String.valueOf(row.get("resourceType"));
            Long resourceId = number(row.get("resourceId"));
            List<String> actions = readActions(row.get("actionsJson"));
            for (String action : actions)
                resources.grantUntil(type, resourceId, principal, action, expiresAt);
        }
        Map<String, Object> decision = new HashMap<>();
        decision.put("id", id);
        decision.put("status", approve ? "APPROVED" : "REJECTED");
        decision.put("decisionNote", clean);
        decision.put("reviewedBy", actors.currentActor().username());
        decision.put("expiresAt", expiresAt);
        if (mapper.decide(decision) != 1) throw new IllegalStateException("审批落库失败");
        audit.record(approve ? "ACCESS_REQUEST_APPROVE" : "ACCESS_REQUEST_REJECT",
                String.valueOf(row.get("resourceType")).toUpperCase(Locale.ROOT), number(row.get("resourceId")), null,
                Map.of("requestId", id));
        return get(id);
    }

    @Scheduled(fixedDelayString = "${bi.access-request.expire-ms:300000}", initialDelayString = "${bi.access-request.expire-ms:300000}")
    public void expireDue() {
        try {
            int grants = resourceMapper.deleteExpiredGrants();
            int requests = mapper.expireApproved();
            if (grants > 0 || requests > 0)
                LOGGER.info("Expired {} resource grants and marked {} access requests EXPIRED", grants, requests);
        } catch (Exception failure) {
            LOGGER.warn("Access-request expiry sweep failed", failure);
        }
    }

    private boolean canApprove(Map<String, Object> row) {
        if (capabilities.has("bi:resource:admin")) return true;
        String type = String.valueOf(row.get("resourceType"));
        Long id = number(row.get("resourceId"));
        return id != null && resources.allowed(type, id, "grant");
    }

    private void requireApproverFor(Map<String, Object> row) {
        if (!canApprove(row)) throw new com.wanfo.bi.security.BiAccessException(403, "无权审批该资源的访问申请");
    }

    private Map<String, Object> decode(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("actions", readActions(row.get("actionsJson")));
        out.remove("actionsJson");
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<String> readActions(Object raw) {
        try {
            if (raw == null) return List.of();
            Object parsed = json.readValue(String.valueOf(raw), Object.class);
            if (parsed instanceof List<?> list) return list.stream().map(String::valueOf).toList();
        } catch (Exception ignored) { }
        return List.of();
    }

    private static String blank(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(max, trimmed.length()));
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    public record CreateRequest(String resourceType, Long resourceId, String assetUrn, String purpose,
            List<String> actions, Integer validDays) { }
}
