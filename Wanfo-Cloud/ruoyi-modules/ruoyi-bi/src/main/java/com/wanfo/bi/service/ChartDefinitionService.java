package com.wanfo.bi.service;

import java.util.*;
import com.wanfo.bi.domain.BiChartDO;
import com.wanfo.bi.domain.chart.ChartAdvancedOptions;
import com.wanfo.bi.domain.chart.ChartTypes;
import com.wanfo.bi.domain.chart.ChartVisualStyle;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.mapper.BiChartMapper;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.security.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persist only validated chart definitions. Results always use the current viewer's security context. */
@Service
public class ChartDefinitionService {
    public record Definition(Integer schemaVersion,String name,String type,QueryModel query,ChartVisualStyle style,ChartAdvancedOptions advanced) {
        public Definition(Integer schemaVersion,String name,String type,QueryModel query) { this(schemaVersion,name,type,query,null,null); }
    }
    /** Component-only overrides; dataset and source predicates are deliberately not writable. */
    public record PanelConfig(String type,List<QueryModel.Dimension> dimensions,List<QueryModel.Metric> metrics,
            List<QueryModel.OrderBy> orderBys,Integer limit,QueryModel.FilterNode filterTree) { }
    public record SaveRequest(Integer version,Definition definition,Boolean templateLease) { }
    private final BiChartMapper mapper;
    private final BiDatasetTableMapper datasets;
    private final ChartQueryService queries;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider caps;
    private final CurrentActorProvider actors;
    private final ObjectMapper json;
    private final ChartTemplateLeaseService templateLeases;
    public ChartDefinitionService(BiChartMapper mapper,BiDatasetTableMapper datasets,ChartQueryService queries,
            BiResourceAuthorizer resources,CapabilityProvider caps,CurrentActorProvider actors,ObjectMapper json,ChartTemplateLeaseService templateLeases) {
        this.mapper=mapper;this.datasets=datasets;this.queries=queries;this.resources=resources;this.caps=caps;this.actors=actors;this.json=json;this.templateLeases=templateLeases;
    }
    public List<Map<String,Object>> list() {
        caps.require("bi:chart:list");List<Map<String,Object>> result=new ArrayList<>();
        for(var row:mapper.list()) {
            if(!resources.allowed("chart",row.getId(),"view") || !dataAllowed(row.getDatasetId())) continue;
            result.add(Map.of("id",row.getId(),"name",row.getName(),"type",row.getType(),"datasetId",row.getDatasetId(),"version",row.getVersion(),
                "resourceActions",java.util.stream.Stream.of("edit","query","remove","grant").filter(a->resources.allowed("chart",row.getId(),a)).toList()));
        }
        return result;
    }
    private boolean dataAllowed(Long id) {
        return caps.has("bi:dataset:query") && resources.allowed("dataset",id,"view") && resources.allowed("dataset",id,"query");
    }
    private BiChartDO require(Long id,String action) {
        resources.require("chart",id,"view");resources.require("chart",id,action);
        BiChartDO row=mapper.get(id);
        if(row==null || row.getDefinitionJson()==null) throw new IllegalArgumentException("图表不存在或尚未迁移为新版定义");
        // Do not imply dataset access by granting a chart.
        caps.require("bi:dataset:query");resources.require("dataset",row.getDatasetId(),"view");resources.require("dataset",row.getDatasetId(),"query");return row;
    }
    private Definition decode(BiChartDO row) {
        Definition d;
        try {d=json.readValue(row.getDefinitionJson(),Definition.class);}catch(RuntimeException e){throw new IllegalArgumentException("图表定义损坏，请联系管理员");}
        if(d==null || d.query()==null || !Objects.equals(row.getDatasetId(),d.query().getDatasetId())) throw new IllegalArgumentException("图表数据集绑定不一致");
        validate(d);return d;
    }
    public Map<String,Object> detail(Long id) {
        caps.require("bi:chart:list");var row=require(id,"view");return Map.of("id",id,"version",row.getVersion(),"definition",decode(row));
    }
    Definition exportForDashboard(Long id) {
        caps.require("bi:chart:list");caps.require("bi:chart:query");
        var row=require(id,"query");return decode(row);
    }
    Definition configureForDashboard(Definition source,PanelConfig config) {
        if(config==null)return source;
        if(config.dimensions()==null || config.metrics()==null || config.orderBys()==null || config.limit()==null
                || config.limit()<1 || config.limit()>1000)throw new IllegalArgumentException("组件配置须包含维度、度量、排序与 1～1000 行上限");
        // Copy both sides: never mutate the shared chart or a caller-owned filter tree.
        var q=json.readValue(json.writeValueAsString(source.query()),QueryModel.class);
        var c=json.readValue(json.writeValueAsString(config),PanelConfig.class);
        q.setDimensions(c.dimensions());q.setMetrics(c.metrics());q.setOrderBys(c.orderBys());q.setLimit(c.limit());
        if(c.filterTree()!=null) {
            if(q.getFilterTree()==null)q.setFilterTree(c.filterTree());
            else {var group=new QueryModel.FilterNode();group.setLogic("AND");group.setChildren(List.of(q.getFilterTree(),c.filterTree()));q.setFilterTree(group);}
        }
        var style=source.style()==null?null:json.readValue(json.writeValueAsString(source.style()),ChartVisualStyle.class);
        var outputs=metricOutputs(q);
        // A reused alias must never inherit formatting from a different field or aggregation.
        for(var original:source.query().getMetrics())for(var changed:q.getMetrics()){
            if(metricOutput(original).equals(metricOutput(changed)) && (!Objects.equals(original.getField(),changed.getField()) || !Objects.equals(original.getAgg(),changed.getAgg())))outputs.remove(metricOutput(original));
        }
        if(style!=null){if(style.getMetrics()!=null)style.setMetrics(style.getMetrics().stream().filter(m->outputs.contains(m.key())).toList());if(style.getReferenceLines()!=null)style.setReferenceLines(style.getReferenceLines().stream().filter(r->outputs.contains(r.key())).toList());}
        var result=new Definition(1,source.name(),c.type(),q,style,source.advanced());validate(result);return result;
    }
    private void validate(Definition d) {
        if(d==null || !Objects.equals(d.schemaVersion(),1) || d.name()==null || d.name().isBlank() || d.name().trim().length()>200
                || d.type()==null || !ChartTypes.allowed(d.type()) || d.query()==null)
            throw new IllegalArgumentException("请选择图表类型并填写有效名称，定义版本须为 1");
        com.wanfo.bi.domain.chart.ChartStyleValidator.validate(d.style(),d.type());
        var q=d.query();
        if(q.getDimensions()!=null && q.getDimensions().stream().anyMatch(dim->dim!=null && dim.getTimeGrain()!=null)) {
            if(!Set.of("bar","hbar","line","area","stack","percent","combo").contains(d.type()))
                throw new IllegalArgumentException("当前图表类型不支持时间粒度，请恢复原始时间后切换类型");
            if(d.advanced()!=null && d.advanced().getDrillFields()!=null && !d.advanced().getDrillFields().isEmpty())
                throw new IllegalArgumentException("时间粒度暂不支持维度下钻，请清除下钻字段或恢复原始时间");
        }
        if(q.getTopN()!=null && !Set.of("bar","hbar","line","area","stack","percent","combo","pie","ring").contains(d.type()))throw new IllegalArgumentException("当前图表类型不支持 Top N");
        preparePivotQuery(d);
        if(q.getTimeComparison()!=null && !Set.of("bar","hbar","line","area").contains(d.type()))throw new IllegalArgumentException("时间比较仅支持柱状图、条形图、折线图和面积图，请先关闭比较再切换类型");
        queries.compileOnly(q); // Same visible-field whitelist, filter-tree and aggregation validation as live queries.
        com.wanfo.bi.domain.chart.ChartAnalysisValidator.validate(d.advanced(),q,d.type());
        var dataset=datasets.selectById(q.getDatasetId());
        if(dataset==null || !"table".equals(dataset.getType())) throw new IllegalArgumentException("本期图表仅支持表数据集");
        if(q.getLimit()!=null && q.getLimit()>1000) throw new IllegalArgumentException("基础图表最多查询 1000 行");
        ChartTypes.validateShape(d.type(), q);
        com.wanfo.bi.domain.chart.ChartStyleValidator.validateBindings(d.style(),metricOutputs(q));
        var tableOutputs=new java.util.HashSet<>(metricOutputs(q));
        for(var dim:q.getDimensions())tableOutputs.add(dim.getAlias()==null || dim.getAlias().isBlank()?dim.getField():dim.getAlias());
        com.wanfo.bi.domain.chart.ChartStyleValidator.validateTableBindings(d.style(),tableOutputs,metricOutputs(q));
        if("pivot".equals(d.type()))validatePivot(d);
        Map<String,String> fieldTypes=new HashMap<>();
        for(var f:queries.datasetFields(q.getDatasetId()))fieldTypes.put((String)f.get("name"),String.valueOf(f.get("type")));
        ChartDrill.validate(d,fieldTypes);
        if(!d.type().equals("table")) {
            Map<String,String> types=new HashMap<>();for(var f:queries.datasetFields(q.getDatasetId()))types.put((String)f.get("name"),String.valueOf(f.get("type")));
            for(var m:q.getMetrics()) {
                String agg=m.getAgg()==null?"NONE":m.getAgg().toUpperCase(Locale.ROOT);
                if(!Set.of("COUNT","COUNT_DISTINCT").contains(agg) && !types.getOrDefault(m.getField(),"").toLowerCase(Locale.ROOT).matches(".*(int|decimal|numeric|float|double|real|number).*"))
                    throw new IllegalArgumentException("图形度量须为数值或计数；文本请使用明细表");
            }
        }
    }
    private static String metricOutput(QueryModel.Metric m){String agg=m.getAgg()==null?"NONE":m.getAgg().toUpperCase(Locale.ROOT);return m.getAlias()!=null&&!m.getAlias().isBlank()?m.getAlias():"NONE".equals(agg)?m.getField():agg.toLowerCase(Locale.ROOT)+"_"+m.getField();}
    private static Set<String> metricOutputs(QueryModel q){
        var keys=new HashSet<String>();for(var m:q.getMetrics())keys.add(metricOutput(m));return keys;
    }
    static void preparePivotQuery(Definition d) {
        if(!"pivot".equals(d.type())){d.query().setPivot(null);return;}
        var a=d.advanced();
        if(a==null || a.getPivotRowFields()==null || a.getPivotColFields()==null)throw new IllegalArgumentException("透视表须配置行列维度");
        d.query().setPivot(new QueryModel.Pivot(a.getPivotRowFields(),a.getPivotColFields()));
    }
    private void validatePivot(Definition d) {
        var a=d.advanced();
        if(a==null || a.getPivotRowFields()==null || a.getPivotColFields()==null)
            throw new IllegalArgumentException("透视表须配置行维度与列维度");
        var rows=a.getPivotRowFields();var cols=a.getPivotColFields();
        if(rows.isEmpty())throw new IllegalArgumentException("透视表须配置至少一个行维度");
        if(cols.isEmpty())throw new IllegalArgumentException("透视表须配置至少一个列维度");
        if(rows.size()>3 || cols.size()>2)throw new IllegalArgumentException("透视表行维度最多 3 个、列维度最多 2 个");
        var q=d.query();
        if(q.getMetrics()==null || q.getMetrics().isEmpty() || q.getMetrics().size()>3)
            throw new IllegalArgumentException("透视表须配置 1～3 个聚合度量");
        for(var m:q.getMetrics()) {
            String agg=m.getAgg()==null?"NONE":m.getAgg().toUpperCase(Locale.ROOT);
            if("NONE".equals(agg))throw new IllegalArgumentException("透视表度量须使用聚合函数");
        }
        var dims=new HashSet<String>();for(var dim:q.getDimensions())dims.add(dim.getField());
        for(var f:rows)if(!dims.contains(f))throw new IllegalArgumentException("透视行维度未加入查询分组："+f);
        for(var f:cols)if(!dims.contains(f))throw new IllegalArgumentException("透视列维度未加入查询分组："+f);
        for(var f:rows)if(cols.contains(f))throw new IllegalArgumentException("字段不能同时作为行与列维度："+f);
        var axes=new HashSet<String>();axes.addAll(rows);axes.addAll(cols);
        if(axes.size()!=rows.size()+cols.size() || dims.size()!=q.getDimensions().size() || !axes.equals(dims))throw new IllegalArgumentException("透视分组须与行列维度完全一致且不重复");
        var style=d.style();
        if(style!=null){
            boolean totals="tree".equals(style.getPivotHierarchy()) || Boolean.TRUE.equals(style.getPivotRowTotals()) || Boolean.TRUE.equals(style.getPivotColTotals()) || Boolean.TRUE.equals(style.getPivotRowSubtotals()) || Boolean.TRUE.equals(style.getPivotColSubtotals());

            if(Boolean.TRUE.equals(style.getPivotRowSubtotals()) && rows.size()<2 || Boolean.TRUE.equals(style.getPivotColSubtotals()) && cols.size()<2)throw new IllegalArgumentException("小计须至少两个对应层级维度");
        }
    }
    private BiChartDO row(Definition d) {
        validate(d);String encoded=json.writeValueAsString(d);
        if(encoded.length()>131072) throw new IllegalArgumentException("图表定义不能超过 128K 字符");
        var row=new BiChartDO();row.setName(d.name().trim());row.setType(d.type());row.setDatasetId(d.query().getDatasetId());row.setDefinitionJson(encoded);
        row.setUpdateBy(actors.currentActor().username());return row;
    }
    @Transactional
    public Map<String,Object> create(SaveRequest request) {
        caps.require("bi:chart:add");if(request==null)throw new IllegalArgumentException("图表定义不能为空");
        var row=row(request.definition());row.setCreateBy(actors.currentActor().username());mapper.insert(row);resources.register("chart",row.getId());
        if(Boolean.TRUE.equals(request.templateLease()))templateLeases.register(row.getId());
        return Map.of("id",row.getId(),"version",0);
    }
    @Transactional
    public Map<String,Object> update(Long id,SaveRequest request) {
        caps.require("bi:chart:edit");require(id,"edit");
        if(request==null || request.version()==null || request.version()<0)throw new IllegalArgumentException("缺少图表版本，请刷新后重试");
        var row=row(request.definition());row.setId(id);row.setVersion(request.version());
        if(mapper.update(row)!=1)throw new BiAccessException(409,"图表已被其他人修改，请重新打开后保存");
        return Map.of("id",id,"version",request.version()+1);
    }
    public Map<String,Object> data(Long id) throws Exception {return data(id,null);}
    public Map<String,Object> data(Long id,Map<String,Object> input) throws Exception {
        caps.require("bi:chart:query");var row=require(id,"query");var d=decode(row);
        return queryDefinitionData(d,input);
    }
    Map<String,Object> exportData(Long id) throws Exception {
        caps.require("bi:chart:query");var d=decode(require(id,"query"));
        var data=queryDefinitionData(d,null);
        if(!"pivot".equals(d.type()))return Map.of("data",data,"definition",d);
        @SuppressWarnings("unchecked") var columns=(List<String>)data.get("columns");
        var labels=FieldDisplayLabels.forChartResult(d,queries.datasetFieldEntities(d.query().getDatasetId()),columns);
        return Map.of("data",data,"definition",d,"fieldLabels",labels);
    }
    private Map<String,Object> queryDefinitionData(Definition d,Map<String,Object> input) throws Exception {
        if(!GeoMapQuery.isMap(d))return queries.query(d.query());
        return mapData(d,input);
    }
    public record MapPreviewRequest(Definition definition,Map<String,Object> input) { }
    public Map<String,Object> previewMap(MapPreviewRequest request) throws Exception {
        caps.require("bi:dataset:query");var d=request.definition();
        if(!GeoMapQuery.isMap(d))throw new IllegalArgumentException("该接口仅用于行政区地图");
        var checked=new Definition(d.schemaVersion(),d.name()==null||d.name().isBlank()?"地图预览":d.name(),d.type(),d.query(),d.style(),d.advanced());validate(checked);
        return mapData(checked,request.input());
    }
    private Map<String,Object> mapData(Definition d,Map<String,Object> input) throws Exception {
        if(input!=null && input.keySet().stream().anyMatch(k->!"drills".equals(k)))throw new IllegalArgumentException("地图运行参数无效");
        DashboardInteraction.validateDrillMap(input,Set.of("chart"));
        var availableFields=queries.datasetFields(d.query().getDatasetId());Map<String,String> types=new HashMap<>();for(var f:availableFields)types.put((String)f.get("name"),String.valueOf(f.get("type")));
        var path=GeoMapQuery.path(d,input,"chart",types);
        var q=json.readValue(json.writeValueAsString(d.query()),QueryModel.class);GeoMapQuery.apply(d,q,path,types);
        var result=new LinkedHashMap<String,Object>(queries.query(q));var context=new LinkedHashMap<String,Object>(GeoMapQuery.context(d,path));
        String currentField=String.valueOf(context.get("field"));String label=availableFields.stream().filter(f->currentField.equals(f.get("name"))).map(f->f.get("showName") instanceof String text&&!text.isBlank()?text:currentField).findFirst().orElse(currentField);context.put("fieldLabel",label);
        result.put("mapContext",context);return result;
    }
    @Transactional
    public Map<String,Object> delete(Long id) {
        caps.require("bi:chart:remove");require(id,"remove");
        if(mapper.delete(id,actors.currentActor().username())!=1)throw new IllegalArgumentException("图表已被看板引用或已删除，不能删除");
        return Map.of("deleted",true);
    }
    public List<Map<String,Object>> grants(Long id) {caps.require("bi:chart:grant");require(id,"grant");return resources.grants("chart",id);}
    public void grant(Long id,Long principal,String action,boolean add) {caps.require("bi:chart:grant");require(id,"grant");resources.change("chart",id,principal,action,add);}
}
