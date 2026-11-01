package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.TaskCenterService;
import com.wanfo.bi.service.TaskPoolGateService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name="数据平台任务中心")
@RestController
@RequestMapping("/bi/tasks")
public class TaskCenterController {
    private final TaskCenterService service;
    public TaskCenterController(TaskCenterService service){this.service=service;}

    @Operation(summary="跨数据平台模块查询任务运行状态（统一任务池）")
    @GetMapping @BiPermission("bi:task:list")
    public Map<String,Object> list(@RequestParam(required=false) String type,@RequestParam(required=false) String status,
            @RequestParam(required=false) String query,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(defaultValue="created") String sort){
        return service.list(type,status,query,page,pageSize,sort);
    }

    @Operation(summary="取消任务中心中支持取消的运行")
    @PostMapping("/{type}/{id}/cancel") @BiPermission("bi:task:list")
    public Map<String,Object> cancel(@PathVariable String type,@PathVariable Long id){return service.cancel(type,id);}

    @Operation(summary="重新提交支持安全重跑的失败任务")
    @PostMapping("/{type}/{id}/retry") @BiPermission("bi:task:list")
    public Map<String,Object> retry(@PathVariable String type,@PathVariable Long id){return service.retry(type,id);}

    @Operation(summary="任务分池暂停/并发配置")
    @GetMapping("/lanes") @BiPermission("bi:task:list")
    public List<Map<String,Object>> lanes(){return service.listLanes();}

    @Operation(summary="更新任务分池（暂停或软并发上限）")
    @PutMapping("/lanes/{lane}") @BiPermission("bi:resource:admin")
    public Map<String,Object> updateLane(@PathVariable String lane,
            @RequestBody TaskPoolGateService.LaneUpdateRequest request){
        return service.updateLane(lane, request);
    }
}
