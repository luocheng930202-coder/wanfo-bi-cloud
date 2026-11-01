package com.wanfo.bi.service;

import com.wanfo.bi.mapper.GlossaryTermMapper;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business glossary / metric dictionary terms aligned with optional bi_metric ids. */
@Service
public class GlossaryTermService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "REVIEW", "CERTIFIED", "DEPRECATED");
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{1,95}");

    public record UpsertRequest(String termCode, String name, String category, String definitionText, String ownerName,
            String certificationStatus, Long metricId) {
    }

    private final GlossaryTermMapper mapper;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;

    public GlossaryTermService(GlossaryTermMapper mapper, CapabilityProvider capabilities, CurrentActorProvider actors) {
        this.mapper = mapper;
        this.capabilities = capabilities;
        this.actors = actors;
    }

    public List<Map<String, Object>> list() {
        capabilities.require("bi:resource:admin");
        return mapper.list();
    }

    public Map<String, Object> get(Long id) {
        capabilities.require("bi:resource:admin");
        Map<String, Object> row = mapper.find(id);
        if (row == null) throw new IllegalArgumentException("术语不存在");
        return row;
    }

    @Transactional
    public Map<String, Object> create(UpsertRequest request) {
        capabilities.require("bi:resource:admin");
        Map<String, Object> row = validate(request);
        row.put("createBy", actors.currentActor().username());
        if (mapper.insert(row) != 1 || !(row.get("id") instanceof Number))
            throw new IllegalStateException("术语创建失败");
        return get(((Number) row.get("id")).longValue());
    }

    @Transactional
    public Map<String, Object> update(Long id, UpsertRequest request) {
        capabilities.require("bi:resource:admin");
        if (mapper.find(id) == null) throw new IllegalArgumentException("术语不存在");
        Map<String, Object> row = validate(request);
        row.put("id", id);
        row.put("updateBy", actors.currentActor().username());
        if (mapper.update(row) != 1) throw new IllegalStateException("术语更新失败");
        return get(id);
    }

    private Map<String, Object> validate(UpsertRequest request) {
        if (request == null) throw new IllegalArgumentException("请求不能为空");
        String code = request.termCode() == null ? "" : request.termCode().trim();
        if (!CODE.matcher(code).matches()) throw new IllegalArgumentException("术语编码须为 2～96 位字母数字_-");
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank() || name.length() > 160) throw new IllegalArgumentException("名称须为 1～160 字");
        String definition = request.definitionText() == null ? "" : request.definitionText().trim();
        if (definition.isBlank() || definition.length() > 2000) throw new IllegalArgumentException("口径说明须为 1～2000 字");
        String status = request.certificationStatus() == null ? "DRAFT"
                : request.certificationStatus().trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("认证状态无效");
        String category = request.category() == null || request.category().isBlank() ? null : request.category().trim();
        if (category != null && category.length() > 64) throw new IllegalArgumentException("分类不能超过 64 字");
        String owner = request.ownerName() == null || request.ownerName().isBlank() ? null : request.ownerName().trim();
        if (owner != null && owner.length() > 64) throw new IllegalArgumentException("责任人不能超过 64 字");
        Long metricId = request.metricId();
        if (metricId != null && mapper.metricExists(metricId) == null)
            throw new IllegalArgumentException("关联指标不存在");
        Map<String, Object> row = new HashMap<>();
        row.put("termCode", code);
        row.put("name", name);
        row.put("category", category);
        row.put("definitionText", definition);
        row.put("ownerName", owner);
        row.put("certificationStatus", status);
        row.put("metricId", metricId);
        return row;
    }
}
