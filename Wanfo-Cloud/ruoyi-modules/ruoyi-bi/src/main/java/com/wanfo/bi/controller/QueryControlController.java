package com.wanfo.bi.controller;
import com.wanfo.bi.query.QueryGovernor;
import com.wanfo.bi.security.BiPermission;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/bi/query")
public class QueryControlController {
 private final QueryGovernor governor;
 public QueryControlController(QueryGovernor governor){this.governor=governor;}
 public record Cancellation(String key){}
 @PostMapping("/cancel")
 @BiPermission(anyOf={"bi:chart:query","bi:dataset:query","bi:dashboard:consume","bi:export:add"})
 public Map<String,Object> cancel(@RequestBody Cancellation input){if(input==null || input.key()==null)throw new IllegalArgumentException("缺少查询取消标识");governor.cancel(input.key());return Map.of("accepted",true);}
}
