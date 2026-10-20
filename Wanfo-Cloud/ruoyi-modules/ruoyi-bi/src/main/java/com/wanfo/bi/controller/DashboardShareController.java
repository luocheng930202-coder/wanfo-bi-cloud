package com.wanfo.bi.controller;

import java.util.List;
import java.util.Map;

import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.security.BiShareAccess;
import com.wanfo.bi.service.DashboardShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BI 看板分享")
@RestController
public class DashboardShareController
{
    private final DashboardShareService shares;

    public DashboardShareController(DashboardShareService shares)
    {
        this.shares = shares;
    }

    public record CreateShareRequest(String dataScope, Integer expireDays, String password) { }

    @Operation(summary = "列出看板分享链接")
    @GetMapping("/bi/dashboard/{id}/shares")
    @BiPermission("bi:dashboard:publish")
    public List<Map<String, Object>> list(@PathVariable Long id)
    {
        return shares.list(id);
    }
    @GetMapping("/bi/dashboard/{id}/share-access-logs")
    @BiPermission("bi:dashboard:publish")
    public List<Map<String,Object>> accessLogs(@PathVariable Long id){return shares.accessLogs(id);}

    @Operation(summary = "创建看板分享链接")
    @PostMapping("/bi/dashboard/{id}/shares")
    @BiPermission("bi:dashboard:publish")
    public Map<String, Object> create(@PathVariable Long id, @RequestBody CreateShareRequest body)
    {
        return shares.create(id, body == null ? null : body.dataScope(),
                body == null ? null : body.expireDays(), body == null ? null : body.password());
    }

    @Operation(summary = "停用分享链接")
    @DeleteMapping("/bi/dashboard/{id}/shares/{shareId}")
    @BiPermission("bi:dashboard:publish")
    public Map<String, Object> revoke(@PathVariable Long id, @PathVariable Long shareId)
    {
        return shares.revoke(id, shareId);
    }

    @Operation(summary = "通过分享链接查看已发布看板")
    @GetMapping("/bi/share/{token}/view")
    @BiShareAccess
    public Map<String, Object> view(@PathVariable String token,
                                    @RequestHeader(value = "X-BI-Share-Password", required = false) String password) throws Exception
    {
        return shares.view(token, password);
    }

    @Operation(summary = "分享看板组件取数")
    @PostMapping("/bi/share/{token}/panels/{key}/data")
    @BiShareAccess
    public Map<String, Object> panelData(@PathVariable String token, @PathVariable String key,
                                         @RequestParam Integer revision,
                                         @RequestBody(required = false) Map<String, Object> input,
                                         @RequestHeader(value = "X-BI-Share-Password", required = false) String password) throws Exception
    {
        return shares.panelData(token, key, revision, input, password);
    }

    @Operation(summary = "分享看板筛选候选值")
    @PostMapping("/bi/share/{token}/controls/{key}/options")
    @BiShareAccess
    public Map<String, Object> options(@PathVariable String token, @PathVariable String key,
                                         @RequestParam Integer revision,
                                         @RequestBody(required = false) Map<String, Object> input,
                                         @RequestHeader(value = "X-BI-Share-Password", required = false) String password) throws Exception
    {
        return shares.options(token, key, revision, input, password);
    }
}
