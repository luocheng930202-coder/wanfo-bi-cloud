package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.ClassificationLineageService;
import com.wanfo.bi.service.ClassificationService;
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

@Tag(name = "资产敏感分级")
@RestController
@RequestMapping("/bi/catalog/classification")
public class ClassificationController {
    private final ClassificationService classifications;
    private final ClassificationLineageService lineage;

    public ClassificationController(ClassificationService classifications, ClassificationLineageService lineage) {
        this.classifications = classifications;
        this.lineage = lineage;
    }

    @Operation(summary = "敏感级别字典")
    @GetMapping("/levels")
    @BiPermission("bi:resource:admin")
    public List<Map<String, Object>> levels() {
        return classifications.levels();
    }

    @Operation(summary = "查询资产有效分级")
    @GetMapping("/assets/{assetId}")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> get(@PathVariable Long assetId) {
        return classifications.get(assetId);
    }

    @Operation(summary = "手工设置资产分级")
    @PutMapping("/assets/{assetId}")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> set(@PathVariable Long assetId, @RequestBody SetRequest body) {
        return classifications.setManual(assetId, body == null ? null : body.levelCode(),
                body == null ? null : body.note());
    }

    @Operation(summary = "按 COLUMN 血缘重算继承分级")
    @PostMapping("/recompute-inheritance")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> recompute() {
        return lineage.recomputeInheritance();
    }

    public record SetRequest(String levelCode, String note) { }
}
