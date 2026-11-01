package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.CatalogAuditService;
import com.wanfo.bi.service.GlossaryTermService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "业务术语表")
@RestController
@RequestMapping("/bi/glossary/terms")
public class GlossaryTermController {
    private final GlossaryTermService terms;
    private final CatalogAuditService audit;

    public GlossaryTermController(GlossaryTermService terms, CatalogAuditService audit) {
        this.terms = terms;
        this.audit = audit;
    }

    @Operation(summary = "列出业务术语 / 指标词典")
    @GetMapping
    @BiPermission("bi:resource:admin")
    public List<Map<String, Object>> list() {
        return terms.list();
    }

    @Operation(summary = "查询术语")
    @GetMapping("/{id}")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> get(@PathVariable Long id) {
        return terms.get(id);
    }

    @Operation(summary = "创建术语")
    @PostMapping
    @BiPermission("bi:resource:admin")
    public Map<String, Object> create(@RequestBody GlossaryTermService.UpsertRequest request) {
        Map<String, Object> row = terms.create(request);
        audit.record("GLOSSARY_TERM_CREATE", "GLOSSARY_TERM", number(row.get("id")), null,
                Map.of("termCode", row.get("termCode"), "certificationStatus", row.get("certificationStatus")));
        return row;
    }

    @Operation(summary = "更新术语")
    @PutMapping("/{id}")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody GlossaryTermService.UpsertRequest request) {
        Map<String, Object> row = terms.update(id, request);
        audit.record("GLOSSARY_TERM_UPDATE", "GLOSSARY_TERM", id, null,
                Map.of("termCode", row.get("termCode"), "certificationStatus", row.get("certificationStatus")));
        return row;
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
