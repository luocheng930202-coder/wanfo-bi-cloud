package com.wanfo.bi.controller;
import java.util.*;
import com.wanfo.bi.security.BiPermission;
import com.wanfo.bi.service.AssetStagingService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/bi/asset/staging")
public class AssetStagingController {
 private final AssetStagingService service;
 public AssetStagingController(AssetStagingService service){this.service=service;}
 @PostMapping @BiPermission(anyOf={"bi:asset:add","bi:dashboard:edit","bi:dashboard:add"}) public Map<String,Object> upload(@RequestBody AssetStagingService.Upload request){return service.upload(request);}
 @GetMapping("/{id}") @BiPermission(anyOf={"bi:asset:add","bi:dashboard:edit","bi:dashboard:add"}) public Map<String,Object> detail(@PathVariable Long id){return service.detail(id);}
 @PostMapping("/release") @BiPermission(anyOf={"bi:asset:add","bi:dashboard:edit","bi:dashboard:add"}) public Map<String,Object> release(@RequestBody AssetStagingService.Release request){return service.release(request);}
 public record Promote(String name) { }
 @PostMapping("/{id}/promote") @BiPermission("bi:asset:add") public Map<String,Object> promote(@PathVariable Long id,@RequestBody(required=false) Promote request){return service.promote(id,request==null?null:request.name());}
}
