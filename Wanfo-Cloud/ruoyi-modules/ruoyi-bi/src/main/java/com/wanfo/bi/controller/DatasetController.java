package com.wanfo.bi.controller;

import java.util.List;
import java.util.Map;

import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.domain.dataset.DatasetJoinModel;
import com.wanfo.bi.service.DatasetModelService;
import com.wanfo.bi.service.DatasetService;
import com.wanfo.bi.service.DatasetQualityRuleService;
import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.PrincipalCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据集接口：选数据源 -> 选表 -> 选字段 -> 存数据集 -> 预览。
 *
 * @author zhengliang
 */
@Tag(name = "BI 数据集")
@RestController
@RequestMapping("/bi/dataset")
public class DatasetController
{
    private final DatasetService service;
    private final DatasetModelService modelService;
    private final DatasetQualityRuleService qualityRules;
    private final CurrentActorProvider actors;
    private final BiResourceAuthorizer resources;
    private final PrincipalCatalog catalog;
    private final com.wanfo.bi.security.DatasetDataPolicy policies;

    public DatasetController(DatasetService service, DatasetModelService modelService, DatasetQualityRuleService qualityRules, CurrentActorProvider actors, BiResourceAuthorizer resources, PrincipalCatalog catalog, com.wanfo.bi.security.DatasetDataPolicy policies)
    {
        this.service = service; this.modelService = modelService; this.qualityRules=qualityRules; this.actors = actors; this.resources = resources; this.catalog = catalog;this.policies=policies;
    }

    // ==================== 目录 ====================

    @Operation(summary = "目录树")
    @GetMapping("/group/tree")
    @BiPermission("bi:dataset:list")
    public List<Map<String, Object>> groupTree()
    {
        return service.groupTree();
    }

    @Operation(summary = "新建目录")
    @PostMapping("/group")
    @BiPermission("bi:dataset:group:add")
    public Map<String, Object> createGroup(@RequestBody Map<String, Object> body)
    {
        String name = (String) body.get("name");
        Long parentId = body.get("parentId") == null ? null
                : Long.valueOf(String.valueOf(body.get("parentId")));
        return service.createGroup(name, parentId, actors.currentActor().username());
    }

    @Operation(summary = "删除目录（需先清空子目录与数据集）")
    @DeleteMapping("/group/{id}")
    @BiPermission("bi:dataset:group:remove")
    public Map<String, Object> deleteGroup(@PathVariable Long id)
    {
        return service.deleteGroup(id, actors.currentActor().username());
    }

    // ==================== 数据集 ====================

    @Operation(summary = "数据集列表")
    @GetMapping("/list")
    @BiPermission("bi:dataset:list")
    public List<Map<String, Object>> list(@RequestParam(required = false) Long groupId)
    {
        return service.list(groupId);
    }

    @Operation(summary = "数据集详情（含字段）")
    @GetMapping("/{id}")
    @BiPermission("bi:dataset:list")
    public Map<String, Object> detail(@PathVariable Long id)
    {
        return service.detail(id);
    }

    @Operation(summary = "创建表型数据集（勾选物理表字段）")
    @PostMapping("/table")
    @BiPermission("bi:dataset:add")
    public Map<String, Object> createTableDataset(@RequestBody Map<String, Object> body) throws Exception
    {
        BiDatasetTableDO form = new BiDatasetTableDO();
        form.setName((String) body.get("name"));
        form.setGroupId(toLong(body.get("groupId")));
        form.setDatasourceId(toLong(body.get("datasourceId")));
        form.setSourceTable((String) body.get("sourceTable"));
        form.setSourceCatalog((String) body.get("sourceCatalog"));
        form.setCacheEnabled((String) body.get("cacheEnabled"));
        form.setRemark((String) body.get("remark"));

        return service.createTableDataset(form, parseFieldSelections(body.get("fields")), actors.currentActor().username());
    }

    private static List<DatasetService.FieldSelection> parseFieldSelections(Object raw)
    {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("字段列表格式无效");
        }
        List<DatasetService.FieldSelection> out = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof String name) {
                out.add(new DatasetService.FieldSelection(name, null, null));
            } else if (item instanceof Map<?, ?> map) {
                String name = stringVal(map.get("name"));
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("字段名不能为空");
                }
                out.add(new DatasetService.FieldSelection(name, stringVal(map.get("showName")), stringVal(map.get("dimFlag"))));
            } else {
                throw new IllegalArgumentException("字段列表格式无效");
            }
        }
        return out;
    }

    private static String stringVal(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    @Operation(summary = "创建 SQL 型数据集")
    @PostMapping("/sql")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> createSqlDataset(@RequestBody Map<String, Object> body) throws Exception
    {
        BiDatasetTableDO form = new BiDatasetTableDO();
        form.setName((String) body.get("name"));
        form.setGroupId(toLong(body.get("groupId")));
        form.setDatasourceId(toLong(body.get("datasourceId")));
        form.setSqlText((String) body.get("sqlText"));
        form.setRemark((String) body.get("remark"));

        List<BiDatasetFieldDO> fields = new java.util.ArrayList<>();
        Object raw = body.get("fields");
        if (raw instanceof List)
        {
            for (Object o : (List<?>) raw)
            {
                @SuppressWarnings("unchecked")
                Map<String, Object> fm = (Map<String, Object>) o;
                BiDatasetFieldDO f = new BiDatasetFieldDO();
                f.setOriginName((String) fm.get("originName"));
                f.setOriginType((String) fm.get("originType"));
                f.setShowName((String) fm.get("showName"));
                f.setDimFlag((String) fm.get("dimFlag"));
                fields.add(f);
            }
        }
        return service.createSqlDataset(form, fields, actors.currentActor().username());
    }

    public record UpdateFieldRequest(String showName, String dimFlag) { }

    @Operation(summary = "更新字段显示名或维度/度量")
    @PutMapping("/{id}/fields/{fieldId}")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> updateField(@PathVariable Long id, @PathVariable Long fieldId,
            @RequestBody UpdateFieldRequest body)
    {
        return service.updateField(id, fieldId, body.showName(), body.dimFlag(), actors.currentActor().username());
    }

    @Operation(summary = "读取关联建模配置")
    @GetMapping("/{id}/model")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> model(@PathVariable Long id)
    {
        return modelService.joinModel(id);
    }

    @Operation(summary = "应用单表关联")
    @PostMapping("/{id}/model/join")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> applyJoin(@PathVariable Long id, @RequestBody DatasetJoinModel body) throws Exception
    {
        return modelService.applyJoin(id, body, actors.currentActor().username());
    }

    @Operation(summary = "移除关联")
    @DeleteMapping("/{id}/model/join")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> clearJoin(@PathVariable Long id) throws Exception
    {
        return modelService.clearJoin(id, actors.currentActor().username());
    }

    public record CalculatedFieldRequest(String originName, String showName, String formula, String dimFlag) { }

    @Operation(summary = "新增计算字段")
    @PostMapping("/{id}/fields/calculated")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> addCalculated(@PathVariable Long id, @RequestBody CalculatedFieldRequest body)
    {
        return modelService.addCalculatedField(id, body.originName(), body.showName(), body.formula(), body.dimFlag(),
                actors.currentActor().username());
    }

    @Operation(summary = "删除计算字段")
    @DeleteMapping("/{id}/fields/calculated/{fieldId}")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> removeCalculated(@PathVariable Long id, @PathVariable Long fieldId)
    {
        return modelService.removeCalculatedField(id, fieldId, actors.currentActor().username());
    }

    @Operation(summary = "预览数据集数据")
    @PostMapping("/{id}/preview")
    @BiPermission("bi:dataset:query")
    public Map<String, Object> preview(@PathVariable Long id,
                                       @RequestParam(defaultValue = "100") int maxRows) throws Exception
    {
        return service.preview(id, maxRows);
    }

    @Operation(summary = "数据集质量检查（最多抽样 100 行）")
    @GetMapping("/{id}/quality")
    @BiPermission("bi:dataset:query")
    public Map<String,Object> quality(@PathVariable Long id)throws Exception{return qualityRules.evaluate(id,service.qualityReport(id));}

    @GetMapping("/{id}/quality/rules")
    @BiPermission("bi:dataset:query")
    public List<DatasetQualityRuleService.Rule> qualityRules(@PathVariable Long id){return qualityRules.get(id);}

    @PutMapping("/{id}/quality/rules")
    @BiPermission("bi:datasource:model")
    public List<DatasetQualityRuleService.Rule> saveQualityRules(@PathVariable Long id,@RequestBody QualityRulesRequest request){return qualityRules.save(id,request.rules(),actors.currentActor().username());}

    public record QualityRulesRequest(List<DatasetQualityRuleService.Rule> rules){}

    @Operation(summary = "删除数据集（级联删除字段）")
    @DeleteMapping("/{id}")
    @BiPermission("bi:dataset:remove")
    public Map<String, Object> delete(@PathVariable Long id)
    {
        return service.delete(id, actors.currentActor().username());
    }

    @GetMapping("/principals")
    @BiPermission("bi:dataset:grant")
    public List<Map<String,Object>> principals(@RequestParam(defaultValue="") String query)
    { return catalog.search(query); }

    @GetMapping("/{id}/grants")
    @BiPermission("bi:dataset:grant")
    public List<Map<String,Object>> grants(@PathVariable Long id)
    {
        resources.require("dataset", id, "grant"); service.detail(id);
        return resources.grants("dataset",id);
    }

    public record GrantRequest(Long principalId, String action) { }

    @PostMapping("/{id}/grants")
    @BiPermission("bi:dataset:grant")
    public Map<String,Object> grant(@PathVariable Long id, @RequestBody GrantRequest request)
    {
        resources.require("dataset",id,"grant"); service.detail(id);
        resources.change("dataset",id,request.principalId(),request.action(),true);
        return Map.of("granted",true);
    }

    @DeleteMapping("/{id}/grants/{principalId}/{action}")
    @BiPermission("bi:dataset:grant")
    public Map<String,Object> revoke(@PathVariable Long id, @PathVariable Long principalId, @PathVariable String action)
    {
        resources.require("dataset",id,"grant"); service.detail(id);
        resources.change("dataset",id,principalId,action,false);
        return Map.of("revoked",true);
    }

    @GetMapping("/{id}/policies")
    @BiPermission("bi:resource:admin")
    public List<com.wanfo.bi.security.DatasetDataPolicy.Rule> policies(@PathVariable Long id) { return policies.list(id); }

    @PostMapping("/{id}/policies")
    @BiPermission("bi:resource:admin")
    public Map<String,Object> addPolicy(@PathVariable Long id,@RequestBody com.wanfo.bi.security.DatasetDataPolicy.Rule rule) {
        policies.add(id,rule);return Map.of("saved",true);
    }

    @DeleteMapping("/{id}/policies/{ruleId}")
    @BiPermission("bi:resource:admin")
    public Map<String,Object> removePolicy(@PathVariable Long id,@PathVariable Long ruleId) {
        policies.remove(id,ruleId);return Map.of("removed",true);
    }

    private Long toLong(Object o)
    {
        return o == null ? null : Long.valueOf(String.valueOf(o));
    }
}

