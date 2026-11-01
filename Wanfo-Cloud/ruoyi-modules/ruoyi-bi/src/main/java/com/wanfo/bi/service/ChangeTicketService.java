package com.wanfo.bi.service;

import com.wanfo.bi.mapper.ChangeTicketMapper;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** S3-04: change tickets from impact packs or manual entry. */
@Service
public class ChangeTicketService {
    private static final Set<String> STATUSES = Set.of("OPEN", "APPROVED", "CLOSED");
    private static final Set<String> SEVERITIES = Set.of("REVIEW", "BLOCKING");

    public record CreateRequest(Long assetId, String title, String severity, String sourceType, Map<String, Object> summary) {
    }

    private final ChangeTicketMapper mapper;
    private final OpsImpactPackService impactPack;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final CatalogAuditService audit;
    private final ObjectMapper json;

    public ChangeTicketService(ChangeTicketMapper mapper, OpsImpactPackService impactPack,
            CapabilityProvider capabilities, CurrentActorProvider actors, CatalogAuditService audit, ObjectMapper json) {
        this.mapper = mapper;
        this.impactPack = impactPack;
        this.capabilities = capabilities;
        this.actors = actors;
        this.audit = audit;
        this.json = json;
    }

    public List<Map<String, Object>> list(Long assetId) {
        capabilities.require("bi:datasource:model");
        return mapper.list(assetId).stream().map(this::enrich).toList();
    }

    public Map<String, Object> get(Long id) {
        capabilities.require("bi:datasource:model");
        Map<String, Object> row = mapper.find(id);
        if (row == null) throw new IllegalArgumentException("变更工单不存在");
        return enrich(row);
    }

    @Transactional
    public Map<String, Object> create(CreateRequest request) {
        capabilities.require("bi:resource:admin");
        if (request == null || request.assetId() == null || request.assetId() < 1)
            throw new IllegalArgumentException("资产编号无效");
        String title = request.title() == null ? "" : request.title().trim();
        if (title.isBlank() || title.length() > 200) throw new IllegalArgumentException("标题须为 1～200 字");
        String severity = normalize(request.severity(), "REVIEW", SEVERITIES);
        String sourceType = request.sourceType() == null || request.sourceType().isBlank()
                ? "MANUAL" : request.sourceType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("IMPACT_PACK", "MANUAL").contains(sourceType)) throw new IllegalArgumentException("来源类型无效");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("assetId", request.assetId());
        row.put("title", title);
        row.put("status", "OPEN");
        row.put("severity", severity);
        row.put("sourceType", sourceType);
        row.put("summaryJson", writeJson(request.summary() == null ? Map.of() : request.summary()));
        row.put("createBy", actors.currentActor().username());
        if (mapper.insert(row) != 1 || !(row.get("id") instanceof Number id))
            throw new IllegalStateException("变更工单创建失败");
        audit.record("CHANGE_TICKET_CREATE", "CHANGE_TICKET", id.longValue(), request.assetId(),
                Map.of("severity", severity, "sourceType", sourceType));
        return get(id.longValue());
    }

    @Transactional
    public Map<String, Object> createFromImpactPack(Long assetId) {
        capabilities.require("bi:resource:admin");
        Map<String, Object> pack = impactPack.packForAsset(assetId);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = pack.get("summary") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        String severity = String.valueOf(summary.getOrDefault("severity", "REVIEW"));
        String title = "Impact pack: asset " + assetId + " (" + severity + ")";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("impactPackSummary", summary);
        payload.put("checklist", pack.get("checklist"));
        return create(new CreateRequest(assetId, title, severity, "IMPACT_PACK", payload));
    }

    @Transactional
    public Map<String, Object> approve(Long id) {
        capabilities.require("bi:resource:admin");
        if (mapper.find(id) == null) throw new IllegalArgumentException("变更工单不存在");
        if (mapper.approve(id, actors.currentActor().username()) != 1)
            throw new IllegalStateException("仅 OPEN 工单可审批");
        audit.record("CHANGE_TICKET_APPROVE", "CHANGE_TICKET", id, null, Map.of());
        return get(id);
    }

    @Transactional
    public Map<String, Object> close(Long id) {
        capabilities.require("bi:resource:admin");
        if (mapper.find(id) == null) throw new IllegalArgumentException("变更工单不存在");
        if (mapper.close(id, actors.currentActor().username()) != 1)
            throw new IllegalStateException("工单状态不可关闭");
        audit.record("CHANGE_TICKET_CLOSE", "CHANGE_TICKET", id, null, Map.of());
        return get(id);
    }

    /** Validates override ticket id for schema migration blocking override. */
    public void assertApprovedOverrideTicket(Long ticketId, Long assetId) {
        if (ticketId == null || ticketId < 1) throw new IllegalArgumentException("overrideBlocking 须填写已审批变更工单号");
        if (mapper.approvedForAsset(ticketId, assetId) == null)
            throw new IllegalArgumentException("变更工单不存在、未审批或与资产不匹配");
    }

    private Map<String, Object> enrich(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        out.put("summary", readJson(row.get("summaryJson")));
        return out;
    }

    private static String normalize(String value, String fallback, Set<String> allowed) {
        String v = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(v)) throw new IllegalArgumentException("严重级别无效");
        return v;
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("工单摘要序列化失败", ex);
        }
    }

    private Object readJson(Object raw) {
        if (raw == null) return Map.of();
        try {
            return json.readValue(String.valueOf(raw), Object.class);
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
