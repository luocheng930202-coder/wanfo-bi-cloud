package com.wanfo.bi.service;

import java.util.*;
import com.wanfo.bi.domain.BiDashboardDO;
import com.wanfo.bi.mapper.BiDashboardMapper;
import com.wanfo.bi.security.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Versioned drafts and immutable publications. Consumption never adopts an author's identity. */
@Service
public class DashboardService {
    public record Panel(String key,Long chartId,String title,Integer x,Integer y,Integer w,Integer h,
            String componentType,DashboardPresentation.PanelStyle style,DashboardContent.Content content,Boolean locked,ChartDefinitionService.PanelConfig chartConfig,String groupKey,Boolean hidden,String parentKey) {
        public Panel(String k,Long id,String t,Integer x,Integer y,Integer w,Integer h,String type,DashboardPresentation.PanelStyle style,DashboardContent.Content content,Boolean locked,ChartDefinitionService.PanelConfig config,String group,Boolean hidden){this(k,id,t,x,y,w,h,type,style,content,locked,config,group,hidden,null);}
        public Panel(String k,Long id,String t,Integer x,Integer y,Integer w,Integer h,String type,DashboardPresentation.PanelStyle style,DashboardContent.Content content,Boolean locked,ChartDefinitionService.PanelConfig config,String group){this(k,id,t,x,y,w,h,type,style,content,locked,config,group,false);}
        public Panel(String k,Long id,String t,Integer x,Integer y,Integer w,Integer h,String type,DashboardPresentation.PanelStyle style,DashboardContent.Content content,Boolean locked,ChartDefinitionService.PanelConfig config){this(k,id,t,x,y,w,h,type,style,content,locked,config,null);}
        public Panel(String k,Long id,String t,Integer x,Integer y,Integer w,Integer h,String type,DashboardPresentation.PanelStyle style,DashboardContent.Content content,Boolean locked){this(k,id,t,x,y,w,h,type,style,content,locked,null);}
        public Panel {locked=locked==null?false:locked;hidden=hidden==null?false:hidden;}
        public Panel(String k,Long id,String t,Integer x,Integer y,Integer w,Integer h,String type,DashboardPresentation.PanelStyle style,DashboardContent.Content content){this(k,id,t,x,y,w,h,type,style,content,false);}
        public Panel(String k,Long id,String t,Integer x,Integer y,Integer w,Integer h,String type,DashboardPresentation.PanelStyle style){this(k,id,t,x,y,w,h,type,style,null);}
        public Panel(String key,Long chartId,String title,Integer x,Integer y,Integer w,Integer h){this(key,chartId,title,x,y,w,h,null,null,null);}
    }
    public record Definition(Integer schemaVersion,String name,List<Panel> panels,String kind,String layout,
            DashboardPresentation.Appearance appearance,DashboardInteraction.Settings interaction,DashboardPresentation.Scene scene) {
        public Definition(Integer v,String n,List<Panel> p,String k,String l,DashboardPresentation.Appearance a,DashboardInteraction.Settings i){this(v,n,p,k,l,a,i,null);}
        public Definition(Integer v,String n,List<Panel> p,String k,String l,DashboardPresentation.Appearance a){this(v,n,p,k,l,a,null);}
        public Definition {interaction=interaction==null?DashboardInteraction.empty():interaction;}
        public Definition(Integer schemaVersion,String name,List<Panel> panels){this(schemaVersion,name,panels,null,null,null,null);}
    }
    public record SaveRequest(Integer version,Definition definition) { }
    public record PublishedPanel(Panel layout,ChartDefinitionService.Definition chart) { }
    public record Publication(Integer schemaVersion,String name,List<PublishedPanel> panels,String kind,String layout,
            DashboardPresentation.Appearance appearance,DashboardInteraction.Settings interaction,DashboardPresentation.Scene scene) {
        public Publication(Integer v,String n,List<PublishedPanel> p,String k,String l,DashboardPresentation.Appearance a,DashboardInteraction.Settings i){this(v,n,p,k,l,a,i,null);}
        public Publication(Integer v,String n,List<PublishedPanel> p,String k,String l,DashboardPresentation.Appearance a){this(v,n,p,k,l,a,null);}
        public Publication {interaction=interaction==null?DashboardInteraction.empty():interaction;}
        public Publication(Integer schemaVersion,String name,List<PublishedPanel> panels){this(schemaVersion,name,panels,null,null,null,null);}
    }
    private final BiDashboardMapper mapper;
    private final ChartDefinitionService charts;
    private final ChartQueryService queries;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider caps;
    private final CurrentActorProvider actors;
    private final ObjectMapper json;
    public DashboardService(BiDashboardMapper mapper,ChartDefinitionService charts,ChartQueryService queries,
            BiResourceAuthorizer resources,CapabilityProvider caps,CurrentActorProvider actors,ObjectMapper json) {
        this.mapper=mapper;this.charts=charts;this.queries=queries;this.resources=resources;this.caps=caps;this.actors=actors;this.json=json;
    }
    private String actor(){return actors.currentActor().username();}
    private BiDashboardDO require(Long id,String action) {
        resources.require("dashboard",id,"view");resources.require("dashboard",id,action);
        var row=mapper.get(id);
        if(row==null || row.getDefinitionJson()==null) throw new IllegalArgumentException("看板不存在或尚未迁移为新版定义");
        return row;
    }
    public List<Map<String,Object>> list() {
        caps.require("bi:dashboard:list");var result=new ArrayList<Map<String,Object>>();
        for(var row:mapper.list()) {
            if(!resources.allowed("dashboard",row.getId(),"view"))continue;
            boolean manages=(caps.has("bi:dashboard:edit") && resources.allowed("dashboard",row.getId(),"edit"))
                    || (caps.has("bi:dashboard:publish") && resources.allowed("dashboard",row.getId(),"publish"));
            if(!manages && row.getPublishedJson()==null)continue;
            String name=manages?row.getName():snapshot(row).name();
            result.add(Map.of("id",row.getId(),"name",name,"version",manages?row.getVersion():row.getPublishedVersion(),"published",row.getPublishedJson()!=null,
                "resourceActions",java.util.stream.Stream.of("edit","publish","consume","grant","remove").filter(a->resources.allowed("dashboard",row.getId(),a)).toList()));
        }
        return result;
    }
    private <T> T read(String value,Class<T> type) {
        try{return json.readValue(value,type);}catch(RuntimeException e){throw new IllegalArgumentException("看板定义无效，请联系管理员");}
    }
    public Map<String,Object> draft(Long id) {
        caps.require("bi:dashboard:edit");var row=require(id,"edit");
        return Map.of("id",id,"version",row.getVersion(),"definition",normalize(read(row.getDefinitionJson(),Definition.class)),"published",row.getPublishedJson()!=null,"resourceActions",java.util.stream.Stream.of("edit","publish","consume").filter(a->resources.allowed("dashboard",id,a)).toList());
    }
    private Panel normalize(Panel p) {
        if(p==null)throw new IllegalArgumentException("看板组件不能为空");
        String type=p.componentType()==null?"chart":p.componentType();
        if(!Set.of("chart","kpi","text","image","border","line","container").contains(type))throw new IllegalArgumentException("不支持的组件类型");
        return new Panel(p.key(),p.chartId(),p.title(),p.x(),p.y(),p.w(),p.h(),type,DashboardPresentation.normalize(p.style()),DashboardContent.normalize(type,p.content()),p.locked(),p.chartConfig(),p.groupKey(),p.hidden(),p.parentKey());
    }
    private Definition normalize(Definition d) {
        if(d==null || !Objects.equals(d.schemaVersion(),1) || d.panels()==null || d.panels().size()>20)throw new IllegalArgumentException("不支持的看板定义版本或组件数量");
        DashboardPresentation.carrier(d.kind(),d.layout());
        if(!"screen".equals(d.kind()) && d.panels().stream().anyMatch(p->p!=null && Set.of("border","line","container").contains(String.valueOf(p.componentType()))))throw new IllegalArgumentException("装饰组件仅支持自由大屏");
        var hiddenKeys=new HashSet<String>();var visibility=new HashMap<String,Boolean>();
        for(var p:d.panels())if(p!=null){
            if(Boolean.TRUE.equals(p.hidden())){if(!"screen".equals(d.kind()))throw new IllegalArgumentException("仅大屏支持隐藏图层");hiddenKeys.add(p.key());}
            if(p.groupKey()!=null){var old=visibility.putIfAbsent(p.groupKey(),Boolean.TRUE.equals(p.hidden()));if(old!=null && old!=Boolean.TRUE.equals(p.hidden()))throw new IllegalArgumentException("组合须整体隐藏或显示");}
        }
        for(var c:d.interaction().controls())if(c!=null && c.targets()!=null && c.targets().stream().anyMatch(t->t!=null && hiddenKeys.contains(t.panel())))throw new IllegalArgumentException("隐藏前请先解除组件的筛选绑定");
        for(var l:d.interaction().links())if(l!=null && (hiddenKeys.contains(l.sourcePanel()) || (l.targets()!=null && l.targets().stream().anyMatch(t->t!=null && hiddenKeys.contains(t.panel())))))throw new IllegalArgumentException("隐藏前请先解除组件的联动绑定");
        var groups=new HashMap<String,Integer>();
        for(var p:d.panels())if(p!=null && p.groupKey()!=null){
            if(!"screen".equals(d.kind()) || !p.groupKey().matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("仅大屏支持有效组合标识");
            groups.merge(p.groupKey(),1,Integer::sum);
        }
        if(groups.values().stream().anyMatch(n->n<2))throw new IllegalArgumentException("组合须至少包含两个组件");
        var normalized=d.panels().stream().map(this::normalize).toList();DashboardContainers.validate(normalized,d.kind());
        return new Definition(1,d.name(),normalized,"screen".equals(d.kind())?"screen":"dashboard","screen".equals(d.kind())?"absolute":"grid",DashboardPresentation.normalize(d.appearance()),d.interaction(),DashboardPresentation.scene(d.kind(),d.scene()));
    }
    private List<PublishedPanel> validate(Definition d) {
        if(d==null || !Objects.equals(d.schemaVersion(),1) || d.name()==null || d.name().isBlank() || d.name().length()>200 || d.panels()==null || d.panels().size()>20)
            throw new IllegalArgumentException("看板须为 v1 定义，名称不超过 200 字符，最多 20 个图表");
        boolean screen="screen".equals(d.kind());int width=screen?d.scene().width():12,height=screen?d.scene().height():120,min=screen?20:2,maxHeight=screen?height:12;
        var keys=new HashSet<String>();var panels=new ArrayList<PublishedPanel>();
        for(var p:d.panels()) {
            if(p==null || p.key()==null || !p.key().matches("[A-Za-z0-9_-]{1,64}") || !keys.add(p.key())
                || p.title()==null || p.title().length()>200 || p.x()==null || p.y()==null || p.w()==null || p.h()==null
                || p.x()<0 || p.y()<0 || p.w()<min || p.h()<min || p.w()>width || p.h()>maxHeight || p.x()>width-p.w() || p.y()>height-p.h())
                throw new IllegalArgumentException("组件标识、图表引用或布局无效；栅格使用 12 列，大屏组件至少 20 像素且不能越界");
            if(!screen)for(var other:panels) {
                var o=other.layout();if(p.x()<o.x()+o.w() && p.x()+p.w()>o.x() && p.y()<o.y()+o.h() && p.y()+p.h()>o.y())throw new IllegalArgumentException("图表组件不能重叠");
            }
            // Revalidate every reference at save/publish time, including unchanged panels.
            boolean data=Set.of("chart","kpi").contains(p.componentType());
            if(data && (p.chartId()==null || p.chartId()<1))throw new IllegalArgumentException("数据组件须绑定图表");
            if(!data && p.chartId()!=null)throw new IllegalArgumentException("静态组件不能引用图表");
            if(!data && p.chartConfig()!=null)throw new IllegalArgumentException("静态组件不能配置图表字段");
            ChartDefinitionService.Definition chart=data?charts.exportForDashboard(p.chartId()):null;
            if(p.chartConfig()!=null)chart=charts.configureForDashboard(chart,p.chartConfig());
            if(p.componentType().equals("kpi")) {
                var q=read(json.writeValueAsString(chart.query()),com.wanfo.bi.domain.query.QueryModel.class);
                if(q.getMetrics().isEmpty() || q.getMetrics().get(0).getAgg()==null || q.getMetrics().get(0).getAgg().equalsIgnoreCase("NONE"))throw new IllegalArgumentException("指标卡须使用聚合度量");
                q.setDimensions(new ArrayList<>());q.setMetrics(new ArrayList<>(List.of(q.getMetrics().get(0))));q.setOrderBys(new ArrayList<>());
                chart=new ChartDefinitionService.Definition(1,chart.name(),"table",q);
            }
            var outputs=new HashSet<String>();if(chart!=null)for(var m:chart.query().getMetrics())outputs.add(m.getAlias()!=null && !m.getAlias().isBlank()?m.getAlias():(m.getAgg()==null || m.getAgg().equalsIgnoreCase("NONE")?m.getField():m.getAgg().toLowerCase(Locale.ROOT)+"_"+m.getField()));
            DashboardPresentation.validateMetricKeys(p.style(),outputs);
            panels.add(new PublishedPanel(p,chart));
        }
        return panels;
    }
    private Definition withInteraction(Definition d,List<PublishedPanel> panels) {
        if(d.interaction().controls().isEmpty() && d.interaction().links().isEmpty())return d;
        Map<String,Map<String,String>> fields=new HashMap<>();
        for(var p:panels){if(p.chart()==null)continue;Map<String,String> f=new HashMap<>();for(var v:queries.datasetFields(p.chart().query().getDatasetId()))f.put((String)v.get("name"),(String)v.get("type"));fields.put(p.layout().key(),f);}
        var interaction=DashboardInteraction.validate(d.interaction(),panels,fields);
        return new Definition(d.schemaVersion(),d.name(),d.panels(),d.kind(),d.layout(),d.appearance(),interaction,d.scene());
    }
    private String document(Definition d){String value=json.writeValueAsString(d);if(value.length()>1048576)throw new IllegalArgumentException("看板文档超过 1M 字符，请减少图片或组件");return value;}
    private void version(Integer value) {
        if(value==null || value<0 || value==Integer.MAX_VALUE)throw new IllegalArgumentException("缺少有效看板版本，请重新打开");
    }
    private void updated(int rows) {if(rows!=1)throw new BiAccessException(409,"看板已被其他人修改，请重新打开后操作");}
    private void bindings(Long id,String kind,List<PublishedPanel> panels) {
        mapper.clearBindings(id,kind);for(var p:panels)if(p.chart()!=null)mapper.bind(id,p.layout().chartId(),p.chart().query().getDatasetId(),kind);
    }
    @Transactional
    public Map<String,Object> create(SaveRequest request) {
        caps.require("bi:dashboard:add");if(request==null)throw new IllegalArgumentException("看板定义不能为空");
        var d=normalize(request.definition());var panels=validate(d);d=withInteraction(d,panels);var row=new BiDashboardDO();row.setName(d.name().trim());row.setDefinitionJson(document(d));row.setCreateBy(actor());row.setUpdateBy(actor());
        mapper.insert(row);resources.register("dashboard",row.getId());bindings(row.getId(),"draft",panels);return Map.of("id",row.getId(),"version",0);
    }
    @Transactional
    public Map<String,Object> update(Long id,SaveRequest request) {
        caps.require("bi:dashboard:edit");require(id,"edit");if(request==null)throw new IllegalArgumentException("看板定义不能为空");version(request.version());
        var d=normalize(request.definition());var panels=validate(d);d=withInteraction(d,panels);var row=new BiDashboardDO();row.setId(id);row.setName(d.name().trim());row.setDefinitionJson(document(d));row.setVersion(request.version());row.setUpdateBy(actor());
        updated(mapper.update(row));bindings(id,"draft",panels);return Map.of("id",id,"version",request.version()+1);
    }
    @Transactional
    public Map<String,Object> publish(Long id,Integer expected) {
        caps.require("bi:dashboard:publish");var row=require(id,"publish");version(expected);
        if(!Objects.equals(row.getVersion(),expected))throw new BiAccessException(409,"草稿已改变，请重新打开后发布");
        var d=normalize(read(row.getDefinitionJson(),Definition.class));var panels=validate(d);if(panels.stream().noneMatch(p->!Boolean.TRUE.equals(p.layout().hidden())))throw new IllegalArgumentException("请先加入可见组件再发布");
        d=withInteraction(d,panels);
        for(var p:panels)if(p.chart()==null && ((p.layout().componentType().equals("text") && p.layout().content().text().isBlank()) || (p.layout().componentType().equals("image") && p.layout().content().image().isEmpty())))throw new IllegalArgumentException("请完善文本或图片内容后发布");
        String snapshot=json.writeValueAsString(new Publication(1,d.name(),panels,d.kind(),d.layout(),d.appearance(),d.interaction(),d.scene()));if(snapshot.length()>1048576)throw new IllegalArgumentException("发布快照超过 1M 字符，请减少组件或筛选条件");
        updated(mapper.publish(id,expected,expected+1,snapshot,actor()));bindings(id,"published",panels);return Map.of("id",id,"version",expected+1,"revision",expected+1);
    }
    @Transactional
    public Map<String,Object> unpublish(Long id,Integer expected) {
        caps.require("bi:dashboard:publish");require(id,"publish");version(expected);updated(mapper.unpublish(id,expected,actor()));mapper.clearBindings(id,"published");return Map.of("version",expected+1);
    }
    private BiDashboardDO published(Long id) {
        caps.require("bi:dashboard:consume");var row=require(id,"consume");
        return publishedRow(row);
    }
    private BiDashboardDO publishedRow(BiDashboardDO row) {
        if(row==null || row.getPublishedJson()==null || row.getPublishedVersion()==null)throw new BiAccessException(403,"看板尚未发布或已下线");
        return row;
    }
    private BiDashboardDO publishedRow(Long id) {
        var row=mapper.get(id);
        if(row==null)throw new IllegalArgumentException("看板不存在");
        return publishedRow(row);
    }
    private Publication snapshot(BiDashboardDO row) {
        var p=read(row.getPublishedJson(),Publication.class);
        if(p==null || !Objects.equals(p.schemaVersion(),1) || p.panels()==null)throw new IllegalArgumentException("发布快照无效");
        DashboardPresentation.carrier(p.kind(),p.layout());
        return new Publication(1,p.name(),p.panels().stream().map(panel->new PublishedPanel(normalize(panel.layout()),panel.chart())).toList(),
                "screen".equals(p.kind())?"screen":"dashboard","screen".equals(p.kind())?"absolute":"grid",DashboardPresentation.normalize(p.appearance()),p.interaction(),DashboardPresentation.scene(p.kind(),p.scene()));
    }
    public Map<String,Object> view(Long id) {
        var row=published(id);return manifest(id,row.getPublishedVersion(),snapshot(row));
    }
    public Map<String,Object> shareView(Long dashboardId) {
        var row=publishedRow(dashboardId);
        return manifest(dashboardId,row.getPublishedVersion(),snapshot(row));
    }
    private Map<String,Object> manifest(Long id,Integer revision,Publication snapshot) {
        // No dataset IDs, query definitions or field names in the consumer manifest.
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id",id);result.put("name",snapshot.name());result.put("revision",revision);result.put("kind",snapshot.kind());result.put("layout",snapshot.layout());result.put("appearance",snapshot.appearance());
        if(snapshot.scene()!=null)result.put("scene",snapshot.scene());
        result.put("panels",snapshot.panels().stream().filter(p->!Boolean.TRUE.equals(p.layout().hidden())).map(p->{var l=p.layout();Map<String,Object> item=new LinkedHashMap<>();item.put("key",l.key());item.put("title",l.title());item.put("type",p.chart()==null?"table":p.chart().type());item.put("x",l.x());item.put("y",l.y());item.put("w",l.w());item.put("h",l.h());item.put("componentType",l.componentType());if(l.parentKey()!=null)item.put("parentKey",l.parentKey());item.put("style",l.style());if(l.content()!=null)item.put("content",l.content());var drill=ChartDrill.fields(p.chart());if(!drill.isEmpty())item.put("drillFields",drill);return item;}).toList());
        result.put("controls",snapshot.interaction().controls().stream().map(c->{Map<String,Object> v=new LinkedHashMap<>();v.put("key",c.key());v.put("label",c.label());v.put("type",c.type());v.put("defaultValue",c.defaultValue());return v;}).toList());
        result.put("links",snapshot.interaction().links().stream().map(l->Map.of("key",l.key(),"sourcePanel",l.sourcePanel())).toList());
        return result;
    }
    private Publication revision(Long id,Integer revision){var row=published(id);if(revision==null || !Objects.equals(revision,row.getPublishedVersion()))throw new BiAccessException(409,"看板发布版本已改变，请刷新看板");return snapshot(row);}
    private PublishedPanel panel(Publication pub,String key){return pub.panels().stream().filter(p->!Boolean.TRUE.equals(p.layout().hidden()) && Objects.equals(p.layout().key(),key)).findFirst().orElseThrow(()->new BiAccessException(403,"组件不属于此看板的发布版本"));}
    public Map<String,Object> panelData(Long id,String key,Integer revision) throws Exception {return panelData(id,key,revision,null);}
    public Map<String,Object> panelData(Long id,String key,Integer revision,Map<String,Object> input) throws Exception {
        return panelDataPublished(revision(id,revision),key,input);
    }
    public Map<String,Object> sharePanelData(Long dashboardId,String key,Integer revision,Map<String,Object> input) throws Exception {
        return panelDataPublished(revisionPublished(dashboardId,revision),key,input);
    }
    private Map<String,Object> panelDataPublished(Publication pub,String key,Map<String,Object> input) throws Exception {
        guardRuntimeInput(pub,input);
        var p=panel(pub,key);var d=p.chart();if(d==null)throw new IllegalArgumentException("静态组件不提供取数接口");var model=filtered(pub,key,input,null);
        var data=queries.queryForDashboard(model);
        return rendered(d,data,pub,key,input);
    }
    private Publication revisionPublished(Long id,Integer revision){var row=publishedRow(id);if(revision==null || !Objects.equals(revision,row.getPublishedVersion()))throw new BiAccessException(409,"看板发布版本已改变，请刷新看板");return snapshot(row);}
    private Map<String,Object> rendered(ChartDefinitionService.Definition d,Map<String,Object> data,Publication pub,String panelKey,Map<String,Object> input) throws Exception {
        var drillFields=ChartDrill.fields(d);
        var path=drillFields.isEmpty()?List.<ChartDrill.Segment>of():ChartDrill.parsePath(input,panelKey,drillFields,fieldTypes(pub,panel(pub,panelKey)));
        var view=drillFields.isEmpty()?d:ChartDrill.presentationDefinition(d,drillFields,path);
        var dimensions=view.query().getDimensions().stream().map(f->f.getAlias()==null || f.getAlias().isBlank()?f.getField():f.getAlias()).toList();
        var metrics=view.query().getMetrics().stream().map(f->f.getAlias()!=null && !f.getAlias().isBlank()?f.getAlias():f.getAgg()==null || f.getAgg().equalsIgnoreCase("NONE")?f.getField():f.getAgg().toLowerCase(Locale.ROOT)+"_"+f.getField()).toList();
        Map<String,Object> presentation=new LinkedHashMap<>();
        presentation.put("type",view.type());presentation.put("dimensions",dimensions);presentation.put("metrics",metrics);
        var queryDims=view.query().getDimensions().stream().map(f->Map.of("field",f.getField(),"alias",f.getAlias()==null || f.getAlias().isBlank()?f.getField():f.getAlias())).toList();
        presentation.put("queryDimensions",queryDims);
        if(view.advanced()!=null && view.advanced().getPivotRowFields()!=null) {
            presentation.put("pivotRowFields",view.advanced().getPivotRowFields());
            presentation.put("pivotColFields",view.advanced().getPivotColFields()==null?List.of():view.advanced().getPivotColFields());
        }
        presentation.putAll(ChartDrill.presentationMeta(drillFields,path));
        @SuppressWarnings("unchecked")
        List<String> columns=(List<String>)data.get("columns");
        if(columns!=null) {
            var fields=queries.datasetFieldEntities(view.query().getDatasetId());
            presentation.put("fieldLabels",FieldDisplayLabels.forChartResult(view,fields,columns));
        }
        return Map.of("data",data,"presentation",presentation);
    }
    private Map<String,String> fieldTypes(Publication pub,PublishedPanel panel) throws Exception {
        if(panel.chart()==null)return Map.of();
        Map<String,String> types=new HashMap<>();
        for(var f:queries.datasetFields(panel.chart().query().getDatasetId()))types.put((String)f.get("name"),String.valueOf(f.get("type")));
        return types;
    }
    private Set<String> drillablePanels(Publication pub) {
        return pub.panels().stream().filter(p->p.chart()!=null && ChartDrill.enabled(p.chart())).map(p->p.layout().key()).collect(java.util.stream.Collectors.toSet());
    }
    private void guardRuntimeInput(Publication pub,Map<String,Object> input) {
        DashboardInteraction.inputs(input,"filters",pub.interaction().controls().stream().map(DashboardInteraction.Control::key).collect(java.util.stream.Collectors.toSet()));
        DashboardInteraction.inputs(input,"links",pub.interaction().links().stream().map(DashboardInteraction.Link::key).collect(java.util.stream.Collectors.toSet()));
        DashboardInteraction.validateDrillMap(input,drillablePanels(pub));
    }
    private com.wanfo.bi.domain.query.QueryModel filtered(Publication pub,String key,Map<String,Object> input,String skip) throws Exception {return filtered(pub,key,input,skip,true);}
    private com.wanfo.bi.domain.query.QueryModel filtered(Publication pub,String key,Map<String,Object> input,String skip,boolean consume) throws Exception {
        var q=read(json.writeValueAsString(panel(pub,key).chart().query()),com.wanfo.bi.domain.query.QueryModel.class);
        if(q.getFilters()==null)q.setFilters(new ArrayList<>());
        var filters=DashboardInteraction.inputs(input,"filters",pub.interaction().controls().stream().map(DashboardInteraction.Control::key).collect(java.util.stream.Collectors.toSet()));
        var links=DashboardInteraction.inputs(input,"links",pub.interaction().links().stream().map(DashboardInteraction.Link::key).collect(java.util.stream.Collectors.toSet()));
        for(var c:pub.interaction().controls()) {
            var first=c.targets().get(0);Object v=DashboardInteraction.value(c.type(),first.valueType(),filters.containsKey(c.key())?filters.get(c.key()):c.defaultValue());
            if(Objects.equals(skip,c.key()))continue;
            for(var t:c.targets())if(t.panel().equals(key))DashboardInteraction.append(q,t.field(),c.type(),v);
        }
        for(var l:pub.interaction().links())if(links.containsKey(l.key())) {
            Object raw=links.get(l.key());Object v=DashboardInteraction.linkValue(l.targets().get(0).valueType(),raw);
            if(l.targets().stream().noneMatch(t->t.panel().equals(key)))continue;
            // Recheck the current consumer's access to the source dimension, including column rules.
            var source=read(json.writeValueAsString(panel(pub,l.sourcePanel()).chart().query()),com.wanfo.bi.domain.query.QueryModel.class);
            if(consume)queries.queryForDashboard(source);else queries.query(source);
            for(var t:l.targets())if(t.panel().equals(key))q.addFilter(t.field(),v==null?"IS_NULL":"EQ",v);
        }
        var chart=panel(pub,key).chart();
        var drillFields=ChartDrill.fields(chart);
        if(!drillFields.isEmpty()) {
            var path=ChartDrill.parsePath(input,key,drillFields,fieldTypes(pub,panel(pub,key)));
            ChartDrill.apply(q,drillFields,path);
        }
        return q;
    }
    public Map<String,Object> options(Long id,String control,Integer revision,Map<String,Object> input) throws Exception {
        return optionsFor(revision(id,revision),control,input,true);
    }
    public Map<String,Object> shareOptions(Long dashboardId,String control,Integer revision,Map<String,Object> input) throws Exception {
        return optionsFor(revisionPublished(dashboardId,revision),control,input,true);
    }
    private Map<String,Object> optionsFor(Publication pub,String control,Map<String,Object> input,boolean consume) throws Exception {
        guardRuntimeInput(pub,input);
        var c=pub.interaction().controls().stream().filter(v->v.key().equals(control) && v.type().equals("enum")).findFirst().orElseThrow(()->new IllegalArgumentException("枚举控件不存在"));
        var target=c.targets().get(0);var q=filtered(pub,target.panel(),input,c.key(),consume);
        q.setDimensions(new ArrayList<>(List.of(new com.wanfo.bi.domain.query.QueryModel.Dimension(target.field(),"option_value"))));q.setMetrics(new ArrayList<>());q.setOrderBys(new ArrayList<>(List.of(new com.wanfo.bi.domain.query.QueryModel.OrderBy("option_value","asc"))));q.setLimit(101);
        var data=consume?queries.queryForDashboard(q):queries.query(q);
        @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)data.get("rows");
        return Map.of("values",rows.stream().map(r->r.get("option_value")).filter(Objects::nonNull).limit(100).toList(),"truncated",rows.size()>100);
    }

    public record PreviewRequest(Integer version,Definition definition,Map<String,Object> input) { }
    private Publication previewContext(Long id,PreviewRequest request) {
        if(request==null)throw new IllegalArgumentException("预览定义不能为空");
        if(id==null)caps.require("bi:dashboard:add");else {
            caps.require("bi:dashboard:edit");var row=require(id,"edit");version(request.version());
            if(!Objects.equals(row.getVersion(),request.version()))throw new BiAccessException(409,"草稿版本已变化，请重新打开后预览");
        }
        var d=normalize(request.definition());var panels=validate(d);d=withInteraction(d,panels);document(d);
        return new Publication(1,d.name(),panels,d.kind(),d.layout(),d.appearance(),d.interaction(),d.scene());
    }
    public Map<String,Object> preview(Long id,PreviewRequest request) throws Exception {
        var pub=previewContext(id,request);Map<String,Object> results=new LinkedHashMap<>();
        // Even a static-only preview rejects unknown input keys.
        guardRuntimeInput(pub,request.input());
        for(var p:pub.panels())if(p.chart()!=null && !Boolean.TRUE.equals(p.layout().hidden())){var data=new LinkedHashMap<>(queries.query(filtered(pub,p.layout().key(),request.input(),null,false)));data.remove("executedSql");data.remove("params");results.put(p.layout().key(),rendered(p.chart(),data,pub,p.layout().key(),request.input()));}
        return Map.of("view",manifest(id==null?0L:id,0,pub),"payloads",results);
    }
    public Map<String,Object> previewOptions(Long id,String control,PreviewRequest request) throws Exception {
        return optionsFor(previewContext(id,request),control,request.input(),false);
    }

    @Transactional
    public Map<String,Object> delete(Long id) {
        caps.require("bi:dashboard:remove");require(id,"remove");updated(mapper.delete(id,actor()));mapper.clearBindings(id,"draft");mapper.clearBindings(id,"published");return Map.of("deleted",true);
    }
    public List<Map<String,Object>> grants(Long id){caps.require("bi:dashboard:grant");require(id,"grant");return resources.grants("dashboard",id);}
    public void grant(Long id,Long principal,String action,boolean add){caps.require("bi:dashboard:grant");require(id,"grant");resources.change("dashboard",id,principal,action,add);}
}
