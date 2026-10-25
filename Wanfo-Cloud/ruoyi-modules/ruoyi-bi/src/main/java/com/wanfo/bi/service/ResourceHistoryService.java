package com.wanfo.bi.service;
import java.util.*;
import com.wanfo.bi.mapper.BiResourceHistoryMapper;
import com.wanfo.bi.security.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceHistoryService {
    private final BiResourceHistoryMapper mapper;
    private final ChartDefinitionService charts;
    private final DashboardService dashboards;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider caps;
    private final ObjectMapper json;
    public ResourceHistoryService(BiResourceHistoryMapper mapper,ChartDefinitionService charts,DashboardService dashboards,BiResourceAuthorizer resources,CapabilityProvider caps,ObjectMapper json){this.mapper=mapper;this.charts=charts;this.dashboards=dashboards;this.resources=resources;this.caps=caps;this.json=json;}
    private void authorize(String type,Long id){
        if(!Set.of("chart","dashboard").contains(type) || id==null || id<1)throw new IllegalArgumentException("资源类型或编号无效");
        caps.require("bi:"+type+":edit");resources.require(type,id,"view");resources.require(type,id,"edit");
        if(type.equals("chart"))charts.detail(id);else dashboards.draft(id);
    }
    public List<BiResourceHistoryMapper.Row> list(String type,Long id){authorize(type,id);return mapper.list(type,id);}
    public Map<String,Object> detail(String type,Long id,int version){
        authorize(type,id);var row=mapper.get(type,id,version);if(row==null)throw new IllegalArgumentException("历史版本不存在或已超出保留范围");
        if(type.equals("chart"))charts.validateHistorical(json.readValue(row.definitionJson(),ChartDefinitionService.Definition.class));else dashboards.validateHistorical(json.readValue(row.definitionJson(),DashboardService.Definition.class));
        return Map.of("version",version,"operation",row.operation(),"definition",json.readTree(row.definitionJson()));
    }
    @Transactional
    public Map<String,Object> restore(String type,Long id,int version,int expected){
        authorize(type,id);var row=mapper.get(type,id,version);if(row==null)throw new IllegalArgumentException("历史版本不存在或已超出保留范围");
        if(expected<0)throw new IllegalArgumentException("请提供当前版本号");
        // Restore is a regular validated save. It never overwrites the publication or bypasses new permissions.
        if(type.equals("chart"))return charts.update(id,new ChartDefinitionService.SaveRequest(expected,json.readValue(row.definitionJson(),ChartDefinitionService.Definition.class),null));
        return dashboards.update(id,new DashboardService.SaveRequest(expected,json.readValue(row.definitionJson(),DashboardService.Definition.class)));
    }
}
