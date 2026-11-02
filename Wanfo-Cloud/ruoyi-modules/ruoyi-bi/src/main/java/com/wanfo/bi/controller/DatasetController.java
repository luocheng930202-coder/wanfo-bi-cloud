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
    private final com.wanfo.bi.service.QualityRuleVersionService qualityVersions;
    private final com.wanfo.bi.service.QualityTaskService qualityTasks;
    private final CurrentActorProvider actors;
    private final BiResourceAuthorizer resources;
    private final PrincipalCatalog catalog;
    private final com.wanfo.bi.security.DatasetDataPolicy policies;

    public DatasetController(DatasetService service, DatasetModelService modelService, DatasetQualityRuleService qualityRules, com.wanfo.bi.service.QualityRuleVersionService qualityVersions, com.wanfo.bi.service.QualityTaskService qualityTasks, CurrentActorProvider actors, BiResourceAuthorizer resources, PrincipalCatalog catalog, com.wanfo.bi.security.DatasetDataPolicy policies)
    {
        this.service = service; this.modelService = modelService; this.qualityRules=qualityRules; this.qualityVersions=qualityVersions; this.qualityTasks=qualityTasks; this.actors = actors; this.resources = resources; this.catalog = catalog;this.policies=policies;
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
        form.setWarehouseModelId(toLong(body.get("warehouseModelId")));
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

    @Operation(summary = "应用多表关联关系")
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

    public record CalculatedOrderRequest(List<Long> orderedIds) { }

    @Operation(summary = "编排计算清洗步骤")
    @PutMapping("/{id}/fields/calculated/order")
    @BiPermission("bi:datasource:model")
    public Map<String,Object> orderCalculated(@PathVariable Long id,@RequestBody CalculatedOrderRequest body)
    {
        return modelService.orderCalculatedFields(id,body.orderedIds(),actors.currentActor().username());
    }

    @Operation(summary = "预览数据集数据")
    @PostMapping("/{id}/preview")
    @BiPermission("bi:dataset:query")
    public Map<String, Object> preview(@PathVariable Long id,
                                       @RequestParam(defaultValue = "100") int maxRows) throws Exception
    {
        return service.preview(id, maxRows);
    }

    @Operation(summary = "数据集质量检查（样本或全量精确扫描）")
    @GetMapping("/{id}/quality")
    @BiPermission("bi:dataset:query")
    public Map<String,Object> quality(@PathVariable Long id,@RequestParam(defaultValue="false") boolean fullScan)throws Exception{return qualityRules.evaluate(id,service.qualityReport(id,fullScan));}

    @GetMapping("/{id}/quality/rules")
    @BiPermission("bi:dataset:query")
    public List<DatasetQualityRuleService.Rule> qualityRules(@PathVariable Long id){return qualityRules.get(id);}

    @PutMapping("/{id}/quality/rules")
    @BiPermission("bi:datasource:model")
    public List<DatasetQualityRuleService.Rule> saveQualityRules(@PathVariable Long id,@RequestBody QualityRulesRequest request){return qualityRules.save(id,request.rules(),actors.currentActor().username());}

    public record QualityRulesRequest(List<DatasetQualityRuleService.Rule> rules){}

    @Operation(summary = "查询数据集质量规则版本")
    @GetMapping("/{id}/quality/versions")
    @BiPermission("bi:dataset:query")
    public List<Map<String,Object>> qualityVersions(@PathVariable Long id){return qualityVersions.list(id);}

    @Operation(summary = "创建质量规则草稿版本")
    @PostMapping("/{id}/quality/versions")
    @BiPermission("bi:datasource:model")
    public Map<String,Object> createQualityVersion(@PathVariable Long id,@RequestBody QualityVersionRequest body){return qualityVersions.createDraft(id,body.name(),body.remark(),body.rules(),actors.currentActor().username());}

    @Operation(summary = "复制质量规则版本为新草稿")
    @PostMapping("/{id}/quality/versions/{versionId}/copy")
    @BiPermission("bi:datasource:model")
    public Map<String,Object> copyQualityVersion(@PathVariable Long id,@PathVariable Long versionId){return qualityVersions.copyDraft(id,versionId,actors.currentActor().username());}

    @GetMapping("/{id}/quality/versions/{versionId}/rules")
    @BiPermission("bi:dataset:query")
    public List<Map<String,Object>> qualityVersionRules(@PathVariable Long id,@PathVariable Long versionId){return qualityVersions.rules(id,versionId);}

    @Operation(summary = "发布质量规则版本")
    @PostMapping("/{id}/quality/versions/{versionId}/publish")
    @BiPermission("bi:datasource:model")
    public Map<String,Object> publishQualityVersion(@PathVariable Long id,@PathVariable Long versionId){return qualityVersions.publish(id,versionId,actors.currentActor().username());}

    public record QualityVersionRequest(String name,String remark,List<com.wanfo.bi.service.QualityRuleVersionService.RuleInput> rules){}

    @Operation(summary = "预览 V25 数据集质量规则迁移")
    @PostMapping("/quality/migrations/v25/preview")
    @BiPermission("bi:datasource:model")
    public Map<String,Object> previewLegacyQualityMigration(@RequestBody(required=false) QualityMigrationRequest body){return qualityVersions.previewLegacyMigration(body==null?null:body.datasetIds());}

    @Operation(summary = "执行 V25 数据集质量规则迁移")
    @PostMapping("/quality/migrations/v25/execute")
    @BiPermission("bi:datasource:model")
    public Map<String,Object> executeLegacyQualityMigration(@RequestBody QualityMigrationRequest body){return qualityVersions.migrateLegacyRules(body==null?null:body.datasetIds(),actors.currentActor().username());}

    public record QualityMigrationRequest(List<Long> datasetIds){}

    @Operation(summary = "列出数据质量任务")
    @GetMapping("/{id}/quality/tasks")
    @BiPermission("bi:dataset:query")
    public List<Map<String,Object>> qualityTasks(@PathVariable Long id){return qualityTasks.list(id);}

    @Operation(summary = "创建手动数据质量任务")
    @PostMapping("/{id}/quality/tasks")
    @BiPermission("bi:datasource:model")
    public Map<String,Object> createQualityTask(@PathVariable Long id,@RequestBody com.wanfo.bi.service.QualityTaskService.CreateTask body){return qualityTasks.create(id,body);}

    @PutMapping("/quality/tasks/{taskId}/status")
    @BiPermission("bi:datasource:model")
    public Map<String,Object> updateQualityTaskStatus(@PathVariable Long taskId,@RequestBody QualityTaskStatusRequest body){return qualityTasks.status(taskId,body.status());}

    @Operation(summary = "执行数据质量任务")
    @PostMapping("/quality/tasks/{taskId}/run")
    @BiPermission("bi:dataset:query")
    public Map<String,Object> triggerQualityTask(@PathVariable Long taskId,@org.springframework.web.bind.annotation.RequestHeader(value="X-Trace-Id",required=false) String traceId){return qualityTasks.trigger(taskId,traceId);}

    @GetMapping("/quality/tasks/{taskId}/runs")
    @BiPermission("bi:dataset:query")
    public List<Map<String,Object>> qualityTaskRuns(@PathVariable Long taskId){return qualityTasks.runs(taskId);}

    @GetMapping("/quality/tasks/{taskId}/runs/{runId}/results")
    @BiPermission("bi:dataset:query")
    public List<Map<String,Object>> qualityTaskRunResults(@PathVariable Long taskId,@PathVariable Long runId){return qualityTasks.results(taskId,runId);}

    public record QualityTaskStatusRequest(String status){}

    @GetMapping("/{id}/quality/issues")
    @BiPermission("bi:dataset:query")
    public List<Map<String,Object>> qualityIssues(@PathVariable Long id){return qualityTasks.issues(id);}

    @GetMapping("/{id}/quality/trend")
    @BiPermission("bi:dataset:query")
    public List<Map<String,Object>> qualityTrend(@PathVariable Long id,@RequestParam(defaultValue="30") int days){return qualityTasks.trend(id,days);}

    @GetMapping("/{id}/quality/score")
    @BiPermission("bi:dataset:query")
    public Map<String,Object> qualityScore(@PathVariable Long id){return qualityTasks.score(id);}

    @GetMapping("/{id}/quality/issues/sla")
    @BiPermission("bi:dataset:query")
    public Map<String,Object> qualityIssueSla(@PathVariable Long id){return qualityTasks.issueSlaSummary(id);}

    @GetMapping("/quality/issues/{issueId}/history")
    @BiPermission("bi:dataset:query")
    public List<Map<String,Object>> qualityIssueHistory(@RequestParam Long datasetId,@PathVariable Long issueId){return qualityTasks.issueHistory(datasetId,issueId);}

    @PostMapping("/quality/issues/{issueId}/claim")
    @BiPermission("bi:dataset:query")
    public Map<String,Object> claimQualityIssue(@RequestParam Long datasetId,@PathVariable Long issueId){return qualityTasks.claimIssue(datasetId,issueId);}

    @Operation(summary = "标记质量问题已修复（待复验）；关闭需后续同规则 PASS 运行")
    @PostMapping("/quality/issues/{issueId}/resolve")
    @BiPermission("bi:dataset:query")
    public Map<String,Object> resolveQualityIssue(@RequestParam Long datasetId,@PathVariable Long issueId,@RequestBody QualityIssueResolutionRequest body){return qualityTasks.resolveIssue(datasetId,issueId,body.note());}

    public record QualityIssueResolutionRequest(String note){}

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

