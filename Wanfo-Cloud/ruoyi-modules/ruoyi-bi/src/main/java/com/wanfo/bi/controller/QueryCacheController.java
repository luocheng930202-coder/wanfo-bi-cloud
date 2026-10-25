package com.wanfo.bi.controller;
import com.wanfo.bi.query.UserQueryCache;
import com.wanfo.bi.security.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/bi/query/cache")
public class QueryCacheController {
    private final UserQueryCache cache;private final CapabilityProvider caps;
    public QueryCacheController(UserQueryCache cache,CapabilityProvider caps){this.cache=cache;this.caps=caps;}
    @GetMapping @BiPermission("bi:resource:admin") public Map<String,Object> stats(){caps.require("bi:resource:admin");return cache.stats();}
    @DeleteMapping @BiPermission("bi:resource:admin") public Map<String,Object> clear(){caps.require("bi:resource:admin");cache.clear(null);return Map.of("cleared",true);}
}
