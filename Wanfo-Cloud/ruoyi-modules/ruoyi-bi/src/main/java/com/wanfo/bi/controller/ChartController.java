package com.wanfo.bi.controller;

import java.util.List;
import java.util.Map;

import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.service.ChartQueryService;
import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.ChartDefinitionService;
import com.wanfo.bi.security.PrincipalCatalog;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图表取数接口（S6）。
 *
 * @author zhengliang
 */
@Tag(name = "BI 图表取数")
@RestController
@RequestMapping("/bi/chart")
public class ChartController
{
    private final ChartQueryService service;
    private final ChartDefinitionService definitions;
    private final PrincipalCatalog principals;

    public ChartController(ChartQueryService service,ChartDefinitionService definitions,PrincipalCatalog principals)
    {
        this.service = service;
        this.definitions=definitions;this.principals=principals;
    }

    @Operation(summary = "数据集字段清单（配置图表时的字段选择器）")
    @GetMapping("/dataset/{datasetId}/fields")
    @BiPermission("bi:dataset:query")
    public List<Map<String, Object>> datasetFields(@PathVariable Long datasetId)
    {
        return service.datasetFields(datasetId);
    }

    @Operation(summary = "执行图表查询（取数）")
    @PostMapping("/data")
    @BiPermission("bi:dataset:query")
    public Map<String, Object> data(@RequestBody QueryModel model) throws Exception
    {
        return service.query(model);
    }

    @Operation(summary = "仅编译 SQL，不执行（用于查看/调试生成的 SQL）")
    @PostMapping("/compile")
    @BiPermission("bi:resource:admin")
    public Map<String, Object> compile(@RequestBody QueryModel model)
    {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("sql", service.compileOnly(model));
        return result;
    }

    @GetMapping("/saved") @BiPermission("bi:chart:list")
    public List<Map<String,Object>> list(){return definitions.list();}
    @GetMapping("/saved/{id}") @BiPermission("bi:chart:list")
    public Map<String,Object> detail(@PathVariable Long id){return definitions.detail(id);}
    @PostMapping("/saved") @BiPermission("bi:chart:add")
    public Map<String,Object> create(@RequestBody ChartDefinitionService.SaveRequest request){return definitions.create(request);}
    @PutMapping("/saved/{id}") @BiPermission("bi:chart:edit")
    public Map<String,Object> update(@PathVariable Long id,@RequestBody ChartDefinitionService.SaveRequest request){return definitions.update(id,request);}
    @PostMapping("/map-preview") @BiPermission("bi:dataset:query")
    public Map<String,Object> previewMap(@RequestBody ChartDefinitionService.MapPreviewRequest request) throws Exception{return definitions.previewMap(request);}
    @PostMapping("/saved/{id}/data") @BiPermission("bi:chart:query")
    public Map<String,Object> savedData(@PathVariable Long id,@RequestBody(required=false) Map<String,Object> input) throws Exception{return definitions.data(id,input);}
    @DeleteMapping("/saved/{id}") @BiPermission("bi:chart:remove")
    public Map<String,Object> delete(@PathVariable Long id){return definitions.delete(id);}
    @GetMapping("/principals") @BiPermission("bi:chart:grant")
    public List<Map<String,Object>> principals(@RequestParam(defaultValue="")String query){return principals.search(query);}
    @GetMapping("/saved/{id}/grants") @BiPermission("bi:chart:grant")
    public List<Map<String,Object>> grants(@PathVariable Long id){return definitions.grants(id);}
    public record GrantRequest(Long principalId,String action) { }
    @PostMapping("/saved/{id}/grants") @BiPermission("bi:chart:grant")
    public Map<String,Object> grant(@PathVariable Long id,@RequestBody GrantRequest request){definitions.grant(id,request.principalId(),request.action(),true);return Map.of("granted",true);}
    @DeleteMapping("/saved/{id}/grants/{principalId}/{action}") @BiPermission("bi:chart:grant")
    public Map<String,Object> revoke(@PathVariable Long id,@PathVariable Long principalId,@PathVariable String action){definitions.grant(id,principalId,action,false);return Map.of("revoked",true);}
}

