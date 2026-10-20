package com.wanfo.bi.controller;
import java.util.*;
import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.ChartTemplateLeaseService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/bi/chart/template-lease")
public class ChartTemplateLeaseController {
 private final ChartTemplateLeaseService service;
 public ChartTemplateLeaseController(ChartTemplateLeaseService service){this.service=service;}
 @PostMapping("/commit") @BiPermission(anyOf={"bi:dashboard:add","bi:dashboard:edit","bi:chart:add"}) public Map<String,Object> commit(@RequestBody ChartTemplateLeaseService.Release request){return service.commit(request);}
 @PostMapping("/release") @BiPermission(anyOf={"bi:dashboard:add","bi:dashboard:edit","bi:chart:add"}) public Map<String,Object> release(@RequestBody ChartTemplateLeaseService.Release request){return service.release(request);}
}
