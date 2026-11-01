package com.wanfo.bi.controller;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.AccessRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "资产访问申请")
@RestController
@RequestMapping("/bi/access-requests")
public class AccessRequestController {
    private final AccessRequestService requests;

    public AccessRequestController(AccessRequestService requests) {
        this.requests = requests;
    }

    @Operation(summary = "我的申请")
    @GetMapping("/mine")
    @BiPermission("bi:datasource:model")
    public List<Map<String, Object>> mine() {
        return requests.mine();
    }

    @Operation(summary = "待我审批（资源 grant 持有人或平台管理员）")
    @GetMapping("/pending")
    @BiPermission("bi:datasource:model")
    public List<Map<String, Object>> pending() {
        return requests.pending();
    }

    @GetMapping("/{id}")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> get(@PathVariable Long id) {
        return requests.get(id);
    }

    @PostMapping
    @BiPermission("bi:datasource:model")
    public Map<String, Object> create(@RequestBody AccessRequestService.CreateRequest request) {
        return requests.create(request);
    }

    public record DecideRequest(boolean approve, String note) { }

    @PostMapping("/{id}/decide")
    @BiPermission("bi:datasource:model")
    public Map<String, Object> decide(@PathVariable Long id, @RequestBody DecideRequest request) {
        return requests.decide(id, request != null && request.approve(), request == null ? null : request.note());
    }
}
