package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.PipelineDefinitionService;
import com.wanfo.bi.service.PipelineDefinitionService.Definition;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.slf4j.MDC;

@Tag(name="数据平台流水线")
@RestController
@RequestMapping("/bi/pipeline")
public class PipelineController {
    private final PipelineDefinitionService service;
    private final com.wanfo.bi.service.PipelineRunService runs;
    private final com.wanfo.bi.service.PipelineScheduleService schedules;
    public PipelineController(PipelineDefinitionService service,com.wanfo.bi.service.PipelineRunService runs,com.wanfo.bi.service.PipelineScheduleService schedules){this.service=service;this.runs=runs;this.schedules=schedules;}

    @Operation(summary="查询可见流水线定义")
    @GetMapping @BiPermission("bi:pipeline:list")
    public List<Map<String,Object>> list(){return service.list();}

    @Operation(summary="创建流水线及首个草稿版本")
    @PostMapping @BiPermission("bi:pipeline:add")
    public Map<String,Object> create(@RequestBody CreateRequest request){return service.create(request.code(),request.name(),request.description());}

    @Operation(summary="读取流水线、版本、节点和连线")
    @GetMapping("/{id}") @BiPermission("bi:pipeline:list")
    public Map<String,Object> detail(@PathVariable Long id){return service.detail(id);}

    @Operation(summary="保存静态 DAG 草稿并执行结构校验")
    @PutMapping("/{pipelineId}/revisions/{revisionId}") @BiPermission("bi:pipeline:edit")
    public Map<String,Object> save(@PathVariable Long pipelineId,@PathVariable Long revisionId,@RequestBody Definition definition){return service.save(pipelineId,revisionId,definition);}

    @Operation(summary="从历史版本复制新草稿")
    @PostMapping("/{pipelineId}/revisions/{revisionId}/copy") @BiPermission("bi:pipeline:edit")
    public Map<String,Object> copy(@PathVariable Long pipelineId,@PathVariable Long revisionId){return service.copy(pipelineId,revisionId);}

    @Operation(summary="校验并发布流水线定义")
    @PostMapping("/{pipelineId}/revisions/{revisionId}/publish") @BiPermission("bi:pipeline:publish")
    public Map<String,Object> publish(@PathVariable Long pipelineId,@PathVariable Long revisionId){return service.publish(pipelineId,revisionId);}

    @Operation(summary="比较两个流水线修订")
    @GetMapping("/{pipelineId}/revisions/{fromRevisionId}/diff/{toRevisionId}") @BiPermission("bi:pipeline:list")
    public Map<String,Object> diff(@PathVariable Long pipelineId,@PathVariable Long fromRevisionId,@PathVariable Long toRevisionId){return service.diff(pipelineId,fromRevisionId,toRevisionId);}

    @Operation(summary="从历史已发布修订创建回滚草稿")
    @PostMapping("/{pipelineId}/revisions/{revisionId}/rollback") @BiPermission("bi:pipeline:rollback")
    public Map<String,Object> rollback(@PathVariable Long pipelineId,@PathVariable Long revisionId){return service.rollback(pipelineId,revisionId);}

    @Operation(summary="查询流水线发布与回滚历史")
    @GetMapping("/{pipelineId}/publish-history") @BiPermission("bi:pipeline:list")
    public List<Map<String,Object>> publishHistory(@PathVariable Long pipelineId,@RequestParam(defaultValue="30") int limit){return service.publishHistory(pipelineId,limit);}

    @Operation(summary="对已发布流程提交受限只读预览")
    @PostMapping("/{pipelineId}/revisions/{revisionId}/runs/preview") @BiPermission("bi:pipeline:run")
    public Map<String,Object> preview(@PathVariable Long pipelineId,@PathVariable Long revisionId,@RequestBody RunRequest request){return runs.submitPreview(pipelineId,revisionId,request.requestKey(),request.timeoutSeconds(),MDC.get("trace_id"));}

    @Operation(summary="对已发布流程提交生产运行并写入数仓逻辑层")
    @PostMapping("/{pipelineId}/revisions/{revisionId}/runs/production") @BiPermission("bi:pipeline:run")
    public Map<String,Object> production(@PathVariable Long pipelineId,@PathVariable Long revisionId,@RequestBody ProductionRunRequest request){
        return runs.submitProduction(pipelineId,revisionId,request.requestKey(),request.timeoutSeconds(),request.targetDatasourceId(),request.maxRows(),MDC.get("trace_id"));
    }

    @Operation(summary="查询流程运行历史")
    @GetMapping("/{pipelineId}/runs") @BiPermission("bi:pipeline:list")
    public List<Map<String,Object>> runs(@PathVariable Long pipelineId,@RequestParam(defaultValue="30") int limit){return runs.list(pipelineId,limit);}

    @Operation(summary="查询流程运行及节点状态")
    @GetMapping("/runs/{runId}") @BiPermission("bi:pipeline:list")
    public Map<String,Object> run(@PathVariable Long runId){return runs.detail(runId);}

    @Operation(summary="取消排队或运行中的流程任务")
    @PostMapping("/runs/{runId}/cancel") @BiPermission("bi:pipeline:run")
    public Map<String,Object> cancel(@PathVariable Long runId){return runs.cancel(runId);}

    @Operation(summary="读取流程生产调度")
    @GetMapping("/{pipelineId}/schedule") @BiPermission("bi:pipeline:list")
    public Map<String,Object> schedule(@PathVariable Long pipelineId){return schedules.get(pipelineId);}

    @Operation(summary="保存流程生产调度")
    @PutMapping("/{pipelineId}/schedule") @BiPermission("bi:pipeline:run")
    public Map<String,Object> saveSchedule(@PathVariable Long pipelineId,@RequestBody com.wanfo.bi.service.PipelineScheduleService.ScheduleRequest request){return schedules.save(pipelineId,request);}

    @Operation(summary="停用流程生产调度")
    @PostMapping("/{pipelineId}/schedule/disable") @BiPermission("bi:pipeline:run")
    public Map<String,Object> disableSchedule(@PathVariable Long pipelineId){return schedules.disable(pipelineId);}

    public record CreateRequest(String code,String name,String description){}
    public record RunRequest(String requestKey,Integer timeoutSeconds){}
    public record ProductionRunRequest(String requestKey,Integer timeoutSeconds,Long targetDatasourceId,Integer maxRows){}
}
