package com.wanfo.bi.controller;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.query.UserQueryCache;
import com.wanfo.bi.security.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/bi/dataset/{id}/cache")
public class DatasetCacheController {
    private final BiDatasetTableMapper mapper;private final BiResourceAuthorizer resources;private final CapabilityProvider caps;private final CurrentActorProvider actors;private final UserQueryCache cache;
    public DatasetCacheController(BiDatasetTableMapper mapper,BiResourceAuthorizer resources,CapabilityProvider caps,CurrentActorProvider actors,UserQueryCache cache){this.mapper=mapper;this.resources=resources;this.caps=caps;this.actors=actors;this.cache=cache;}
    private BiDatasetTableDO require(Long id){
        caps.require("bi:datasource:model");resources.require("dataset",id,"view");var row=mapper.selectById(id);
        if(row==null || !"table".equals(row.getType()))throw new IllegalArgumentException("仅表数据集支持缓存设置");
        resources.require("datasource",row.getDatasourceId(),"model");
        if(row.getGroupId()!=null && row.getGroupId()!=0)resources.require("dataset-group",row.getGroupId(),"edit");return row;
    }
    @GetMapping @BiPermission("bi:datasource:model") public Map<String,Object> get(@PathVariable Long id){var row=require(id);return Map.of("enabled","1".equals(row.getCacheEnabled()),"ttl",row.getCacheTtl()==null?300:row.getCacheTtl());}
    public record Settings(boolean enabled,int ttl){}
    @PutMapping @BiPermission("bi:datasource:model") public Map<String,Object> set(@PathVariable Long id,@RequestBody Settings input){
        require(id);if(input.ttl()<5 || input.ttl()>300)throw new IllegalArgumentException("缓存时间须为 5～300 秒");
        var row=new BiDatasetTableDO();row.setId(id);row.setCacheEnabled(input.enabled()?"1":"0");row.setCacheTtl(input.ttl());row.setUpdateBy(actors.currentActor().username());
        if(mapper.update(row)!=1)throw new IllegalArgumentException("数据集更新失败");cache.clear(id);return Map.of("enabled",input.enabled(),"ttl",input.ttl());
    }
}
