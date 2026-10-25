package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.ReportSubscriptionService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bi/subscription")
public class ReportSubscriptionController {
    private final ReportSubscriptionService service;
    public ReportSubscriptionController(ReportSubscriptionService service){this.service=service;}
    @GetMapping @BiPermission("bi:subscription:list") public List<Map<String,Object>> list(){return service.list();}
    @PostMapping @BiPermission("bi:subscription:add") public Map<String,Object> create(@RequestBody ReportSubscriptionService.Create request){return service.create(request);}
    @PutMapping("/{id}") @BiPermission("bi:subscription:edit") public Map<String,Object> update(@PathVariable Long id,@RequestBody ReportSubscriptionService.Update request){return service.update(id,request);}
    @DeleteMapping("/{id}") @BiPermission("bi:subscription:remove") public Map<String,Object> delete(@PathVariable Long id){return service.delete(id);}
    @GetMapping("/{id}/history") @BiPermission("bi:subscription:list") public List<Map<String,Object>> history(@PathVariable Long id){return service.history(id);}
    @GetMapping("/notifications") @BiPermission("bi:subscription:list") public List<Map<String,Object>> notifications(){return service.notifications();}
    @PutMapping("/notifications/{id}/read") @BiPermission("bi:subscription:list") public Map<String,Object> readNotification(@PathVariable Long id){return service.markNotificationRead(id);}
    @GetMapping("/admin/audit") @BiPermission("bi:resource:admin") public List<Map<String,Object>> globalAudit(){return service.globalAudit();}
}
