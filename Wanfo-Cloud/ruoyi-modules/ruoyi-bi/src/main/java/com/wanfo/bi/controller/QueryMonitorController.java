package com.wanfo.bi.controller;
import com.wanfo.bi.query.QueryGovernor;
import com.wanfo.bi.security.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/bi/query/monitor")
public class QueryMonitorController {
 private final QueryGovernor governor;private final CapabilityProvider capabilities;
 public QueryMonitorController(QueryGovernor governor,CapabilityProvider capabilities){this.governor=governor;this.capabilities=capabilities;}
 @GetMapping @BiPermission("bi:resource:admin")
 public Map<String,Object> status(){capabilities.require("bi:resource:admin");return governor.monitor();}
 public record Cancellation(String requestId){}
 @PostMapping("/cancel") @BiPermission("bi:resource:admin")
 public Map<String,Object> cancel(@RequestBody Cancellation input){capabilities.require("bi:resource:admin");if(input==null || input.requestId()==null)throw new IllegalArgumentException("缺少排查编号");return Map.of("accepted",governor.cancelRequest(input.requestId()));}
}
