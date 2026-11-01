package com.wanfo.bi.service;

import com.wanfo.bi.mapper.PipelineMapper;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.DatasetDataPolicy;
import com.wanfo.bi.mapper.BiDatasetFieldMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Static, versioned pipeline DAG definitions. This service deliberately does not execute nodes. */
@Service
public class PipelineDefinitionService {
    private static final Set<String> NODE_TYPES = Set.of("SOURCE_DATASET", "JOIN", "FILTER", "TRANSFORM", "QUALITY_GATE", "WAREHOUSE_PUBLISH");
    private static final Map<String,Set<String>> CONFIG_KEYS = Map.of(
            "SOURCE_DATASET",Set.of("datasetId","alias"),
            "JOIN",Set.of("joinType","leftKeys","rightKeys","relationship"),
            "FILTER",Set.of("conditions"),
            "TRANSFORM",Set.of("mappings"),
            "QUALITY_GATE",Set.of("ruleSetDatasetId","ruleSetId","minPassRate","severity","blockOnFailure"),
            "WAREHOUSE_PUBLISH",Set.of("layer","modelCode","writeMode","partitionField"));
    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,95}");
    private final PipelineMapper mapper;
    private final BiResourceAuthorizer resources;
    private final CurrentActorProvider actors;
    private final ObjectMapper json;
    private final BiDatasetFieldMapper datasetFields;
    private final DatasetDataPolicy dataPolicies;
    private final QualityRuleVersionService qualityRules;

    public PipelineDefinitionService(PipelineMapper mapper, BiResourceAuthorizer resources, CurrentActorProvider actors,
            BiDatasetFieldMapper datasetFields, DatasetDataPolicy dataPolicies, QualityRuleVersionService qualityRules, ObjectMapper json) {
        this.mapper=mapper; this.resources=resources; this.actors=actors; this.datasetFields=datasetFields; this.dataPolicies=dataPolicies; this.qualityRules=qualityRules; this.json=json;
    }

    public List<Map<String,Object>> list() { return mapper.list().stream().filter(row -> resources.allowed("pipeline", ((Number)row.get("id")).longValue(), "view")).toList(); }

    @Transactional
    public Map<String,Object> create(String code,String name,String description) {
        String safeCode=code==null?"":code.trim(); String safeName=name==null?"":name.trim();
        if(!safeCode.matches("[A-Za-z][A-Za-z0-9_-]{2,95}")) throw new IllegalArgumentException("流水线编码需为 3～96 位字母、数字、下划线或短横线");
        if(safeName.isBlank()||safeName.length()>160) throw new IllegalArgumentException("流水线名称不能为空且不能超过 160 字");
        String actor=actor(); Map<String,Object> row=new HashMap<>();row.put("code",safeCode);row.put("name",safeName);row.put("description",limit(description,1000));row.put("actor",actor);
        if(mapper.insertPipeline(row)!=1||!(row.get("id") instanceof Number id)) throw new IllegalStateException("创建流水线失败");
        Long pipelineId=id.longValue(); resources.register("pipeline",pipelineId);
        Map<String,Object> rev=new HashMap<>();rev.put("pipelineId",pipelineId);rev.put("revision",1);rev.put("inputSchema","{}");rev.put("outputSchema","{}");rev.put("parameters","{}");rev.put("actor",actor);rev.put("remark","Initial draft");
        mapper.insertRevision(rev);
        return detail(pipelineId);
    }

    public Map<String,Object> detail(Long id) {
        resources.require("pipeline",id,"view"); Map<String,Object> pipeline=mapper.pipeline(id);
        if(pipeline==null) throw new IllegalArgumentException("流水线不存在");
        List<Map<String,Object>> revisions=mapper.revisions(id);
        List<Map<String,Object>> details=new ArrayList<>();
        for(Map<String,Object> revision:revisions){Map<String,Object> item=new LinkedHashMap<>(revision);Long revisionId=((Number)revision.get("id")).longValue();item.put("nodes",mapper.nodes(revisionId));item.put("edges",mapper.edges(revisionId));details.add(item);}
        pipeline.put("revisions",details);return pipeline;
    }

    @Transactional
    public Map<String,Object> save(Long pipelineId,Long revisionId,Definition definition) {
        resources.require("pipeline",pipelineId,"edit"); Map<String,Object> revision=requireRevision(pipelineId,revisionId);
        if(!"DRAFT".equals(String.valueOf(revision.get("status")))) throw new IllegalArgumentException("只能修改草稿版本");
        Normalized normalized=validate(definition);
        mapper.deleteEdges(revisionId);mapper.deleteNodes(revisionId);
        int order=0;
        for(Node node:normalized.nodes()) mapper.insertNode(revisionId,node.key(),node.type(),node.name(),write(node.config()),write(node.inputSchema()),write(node.outputSchema()),node.x(),node.y(),order++);
        order=0;
        for(Edge edge:normalized.edges()) mapper.insertEdge(revisionId,edge.source(),edge.sourcePort(),edge.target(),edge.targetPort(),edge.type(),order++);
        String input=write(normalized.inputSchema()),output=write(normalized.outputSchema()),params=write(normalized.parameters());
        if(definition.expectedVersion()==null||definition.expectedVersion()<0)throw new IllegalArgumentException("保存草稿必须提供当前版本号，请刷新后重试");
        if(mapper.updateRevision(pipelineId,revisionId,input,output,params,limit(definition.remark(),500),definition.expectedVersion())!=1) throw new IllegalStateException("草稿已被其他操作更新或发布，请刷新后重试");
        Map<String,Object> result=new LinkedHashMap<>(requireRevision(pipelineId,revisionId));result.put("nodes",mapper.nodes(revisionId));result.put("edges",mapper.edges(revisionId));result.put("validation",Map.of("valid",true,"nodeCount",normalized.nodes().size(),"edgeCount",normalized.edges().size()));return result;
    }

    @Transactional
    public Map<String,Object> copy(Long pipelineId,Long sourceRevisionId) {
        resources.require("pipeline",pipelineId,"edit");Map<String,Object> source=requireRevision(pipelineId,sourceRevisionId);
        int next=mapper.nextRevision(pipelineId);Map<String,Object> draft=new HashMap<>();draft.put("pipelineId",pipelineId);draft.put("revision",next);
        draft.put("inputSchema",jsonValue(source.get("inputSchema")));draft.put("outputSchema",jsonValue(source.get("outputSchema")));draft.put("parameters",jsonValue(source.get("parameters")));
        draft.put("actor",actor());draft.put("remark","Copied from revision "+source.get("revision"));mapper.insertRevision(draft);
        Long newId=((Number)draft.get("id")).longValue();
        Definition cloned=new Definition(mapper.nodes(sourceRevisionId).stream().map(this::nodeFromRow).toList(),mapper.edges(sourceRevisionId).stream().map(this::edgeFromRow).toList(),asMap(source.get("inputSchema")),asMap(source.get("outputSchema")),asMap(source.get("parameters")),String.valueOf(draft.get("remark")),0);
        // Initial inserts use the same validator and protect against copying stale/unsafe definitions.
        save(pipelineId,newId,cloned);return detail(pipelineId);
    }

    @Transactional
    public Map<String,Object> publish(Long pipelineId,Long revisionId) {
        resources.require("pipeline",pipelineId,"publish");Map<String,Object> revision=requireRevision(pipelineId,revisionId);
        if(!"DRAFT".equals(String.valueOf(revision.get("status"))))throw new IllegalArgumentException("只有草稿版本可以发布");
        Definition def=new Definition(mapper.nodes(revisionId).stream().map(this::nodeFromRow).toList(),mapper.edges(revisionId).stream().map(this::edgeFromRow).toList(),asMap(revision.get("inputSchema")),asMap(revision.get("outputSchema")),asMap(revision.get("parameters")),String.valueOf(revision.getOrDefault("remark","")),((Number)revision.getOrDefault("lockVersion",0)).intValue());
        Normalized normalized=validate(def);String hash=sha256(write(Map.of("nodes",normalized.nodes(),"edges",normalized.edges(),"inputSchema",normalized.inputSchema(),"outputSchema",normalized.outputSchema(),"parameters",normalized.parameters())));int expectedVersion=((Number)revision.getOrDefault("lockVersion",0)).intValue();String currentActor=actor();
        if(mapper.publishRevision(pipelineId,revisionId,hash,currentActor,expectedVersion)!=1)throw new IllegalStateException("发布冲突：草稿已被其他操作更新或发布，请刷新后重试");
        Map<String,Object> pipeline=mapper.pipeline(pipelineId);Long previousRevision=longValue(pipeline==null?null:pipeline.get("currentRevisionId"));
        if(mapper.markPublished(pipelineId,revisionId,previousRevision,currentActor)!=1)throw new IllegalStateException("发布冲突：当前生效版本已变化，请刷新后重试");
        String action=revision.get("rollbackFromRevisionId")==null?"PUBLISH":"ROLLBACK";String reason="ROLLBACK".equals(action)?"由历史版本 "+revision.get("rollbackFromRevisionId")+" 创建的新修订发布":null;
        mapper.insertPublishHistory(pipelineId,previousRevision,revisionId,action,currentActor,reason);return detail(pipelineId);
    }

    @Transactional
    public Map<String,Object> rollback(Long pipelineId,Long sourceRevisionId){
        resources.require("pipeline",pipelineId,"rollback");resources.require("pipeline",pipelineId,"edit");Map<String,Object> source=requireRevision(pipelineId,sourceRevisionId);
        if(!"PUBLISHED".equals(String.valueOf(source.get("status"))))throw new IllegalArgumentException("只能从已发布历史版本创建回滚草稿");
        int next=mapper.nextRevision(pipelineId);Map<String,Object> draft=new HashMap<>();draft.put("pipelineId",pipelineId);draft.put("revision",next);draft.put("inputSchema",jsonValue(source.get("inputSchema")));draft.put("outputSchema",jsonValue(source.get("outputSchema")));draft.put("parameters",jsonValue(source.get("parameters")));draft.put("actor",actor());draft.put("remark","Rollback draft from revision "+source.get("revision"));
        mapper.insertRevision(draft);Long draftId=((Number)draft.get("id")).longValue();
        Definition cloned=new Definition(mapper.nodes(sourceRevisionId).stream().map(this::nodeFromRow).toList(),mapper.edges(sourceRevisionId).stream().map(this::edgeFromRow).toList(),asMap(source.get("inputSchema")),asMap(source.get("outputSchema")),asMap(source.get("parameters")),String.valueOf(draft.get("remark")),0);
        if(mapper.markRollbackDraft(pipelineId,draftId,sourceRevisionId)!=1)throw new IllegalStateException("记录回滚来源失败");save(pipelineId,draftId,cloned);return detail(pipelineId);
    }

    public Map<String,Object> diff(Long pipelineId,Long fromRevisionId,Long toRevisionId){
        resources.require("pipeline",pipelineId,"view");Map<String,Object> from=requireRevision(pipelineId,fromRevisionId),to=requireRevision(pipelineId,toRevisionId);
        Map<String,Map<String,Object>> before=nodesByKey(fromRevisionId),after=nodesByKey(toRevisionId);List<Map<String,Object>> addedNodes=new ArrayList<>(),removedNodes=new ArrayList<>(),changedNodes=new ArrayList<>();
        for(String key:after.keySet())if(!before.containsKey(key))addedNodes.add(nodeSummary(after.get(key)));else{Map<String,Object> old=before.get(key),now=after.get(key);List<String> changes=new ArrayList<>();for(String field:List.of("nodeType","name","config","inputSchema","outputSchema","positionX","positionY"))if(!canonical(old.get(field)).equals(canonical(now.get(field))))changes.add(field);if(!changes.isEmpty())changedNodes.add(Map.of("nodeKey",key,"fields",changes,"before",nodeSummary(old),"after",nodeSummary(now)));}
        for(String key:before.keySet())if(!after.containsKey(key))removedNodes.add(nodeSummary(before.get(key)));
        Set<String> beforeEdges=edgeKeys(fromRevisionId),afterEdges=edgeKeys(toRevisionId);List<String> addedEdges=afterEdges.stream().filter(edge->!beforeEdges.contains(edge)).sorted().toList(),removedEdges=beforeEdges.stream().filter(edge->!afterEdges.contains(edge)).sorted().toList();List<String> changedMetadata=new ArrayList<>();for(String field:List.of("inputSchema","outputSchema","parameters","remark"))if(!canonical(from.get(field)).equals(canonical(to.get(field))))changedMetadata.add(field);
        Map<String,Object> result=new LinkedHashMap<>();result.put("from",revisionSummary(from));result.put("to",revisionSummary(to));result.put("same",addedNodes.isEmpty()&&removedNodes.isEmpty()&&changedNodes.isEmpty()&&addedEdges.isEmpty()&&removedEdges.isEmpty()&&changedMetadata.isEmpty());result.put("addedNodes",addedNodes);result.put("removedNodes",removedNodes);result.put("changedNodes",changedNodes);result.put("addedEdges",addedEdges);result.put("removedEdges",removedEdges);result.put("changedMetadata",changedMetadata);return result;
    }

    public List<Map<String,Object>> publishHistory(Long pipelineId,int limit){resources.require("pipeline",pipelineId,"view");return mapper.publishHistory(pipelineId,Math.max(1,Math.min(limit,100)));}

    private Map<String,Map<String,Object>> nodesByKey(Long revisionId){Map<String,Map<String,Object>> result=new LinkedHashMap<>();for(Map<String,Object> node:mapper.nodes(revisionId))result.put(String.valueOf(node.get("nodeKey")),node);return result;}
    private Set<String> edgeKeys(Long revisionId){Set<String> result=new HashSet<>();for(Map<String,Object> edge:mapper.edges(revisionId))result.add(String.join("|",String.valueOf(edge.get("sourceNodeKey")),String.valueOf(edge.get("sourcePort")),String.valueOf(edge.get("targetNodeKey")),String.valueOf(edge.get("targetPort")),String.valueOf(edge.get("edgeType"))));return result;}
    private Map<String,Object> nodeSummary(Map<String,Object> node){Map<String,Object> result=new LinkedHashMap<>();for(String key:List.of("nodeKey","nodeType","name","config","positionX","positionY"))result.put(key,node.get(key));return result;}
    private Map<String,Object> revisionSummary(Map<String,Object> revision){Map<String,Object> result=new LinkedHashMap<>();for(String key:List.of("id","revision","status","definitionHash","lockVersion","rollbackFromRevisionId","publishBy","publishTime"))result.put(key,revision.get(key));return result;}
    private String canonical(Object value){if(value instanceof String text&&!text.isBlank())try{return write(json.readValue(text,Object.class));}catch(RuntimeException ignored){}return write(value==null?Map.of():value);}

    private Normalized validate(Definition definition) {
        if(definition==null||definition.nodes()==null||definition.nodes().isEmpty()||definition.nodes().size()>200)throw new IllegalArgumentException("流水线节点数量需为 1～200");
        if(definition.edges()==null||definition.edges().size()>500)throw new IllegalArgumentException("流水线连线最多 500 条");
        List<Node> nodes=new ArrayList<>();Map<String,Node> byKey=new LinkedHashMap<>();int sourceCount=0,outputCount=0;
        for(Node raw:definition.nodes()){
            if(raw==null||raw.key()==null||!KEY.matcher(raw.key()).matches()||byKey.containsKey(raw.key()))throw new IllegalArgumentException("节点 key 格式无效或重复");
            if(raw.type()==null||!NODE_TYPES.contains(raw.type()))throw new IllegalArgumentException("不支持的节点类型");
            String name=raw.name()==null?"":raw.name().trim();if(name.isBlank()||name.length()>160)throw new IllegalArgumentException("节点名称不能为空且不能超过 160 字");
            Map<String,Object> config=safeObject(raw.config(),"节点配置");Map<String,Object> input=safeObject(raw.inputSchema(),"输入结构");Map<String,Object> output=safeObject(raw.outputSchema(),"输出结构");
            if(config.size()>32||write(config).length()>8192)throw new IllegalArgumentException("单个节点配置过大");
            if(containsForbidden(config))throw new IllegalArgumentException("节点配置不能包含凭证、任意 SQL 或脚本");
            if(config.keySet().stream().anyMatch(key->!CONFIG_KEYS.get(raw.type()).contains(key)))throw new IllegalArgumentException("节点配置包含该节点类型不支持的字段");
            Node node=new Node(raw.key(),raw.type(),name,config,input,output,clamp(raw.x()),clamp(raw.y()));nodes.add(node);byKey.put(node.key(),node);
            if("SOURCE_DATASET".equals(node.type())) {sourceCount++;Long dataset=longValue(config.get("datasetId"));if(dataset==null)throw new IllegalArgumentException("数据集来源节点必须选择数据集");resources.require("dataset",dataset,"view");resources.require("dataset",dataset,"query");}
            if("QUALITY_GATE".equals(node.type())) {Long dataset=longValue(config.get("ruleSetDatasetId")),ruleSet=longValue(config.get("ruleSetId"));if(dataset==null||ruleSet==null)throw new IllegalArgumentException("质量门禁必须选择数据集和已发布规则版本");double rate=decimal(config.getOrDefault("minPassRate",100));if(!Double.isFinite(rate)||rate<0||rate>100)throw new IllegalArgumentException("质量门禁通过率须在 0～100 之间");String severity=String.valueOf(config.getOrDefault("severity","ERROR"));if(!Set.of("INFO","WARN","ERROR","FATAL").contains(severity))throw new IllegalArgumentException("质量门禁阻断等级无效");if(config.containsKey("blockOnFailure")&&!(config.get("blockOnFailure") instanceof Boolean)&&!(config.get("blockOnFailure") instanceof Number)&&!("true".equalsIgnoreCase(String.valueOf(config.get("blockOnFailure")))||"false".equalsIgnoreCase(String.valueOf(config.get("blockOnFailure")))))throw new IllegalArgumentException("blockOnFailure 须为布尔值");qualityRules.requirePublishedSet(dataset,ruleSet);}
            if("WAREHOUSE_PUBLISH".equals(node.type())) {outputCount++;Object layer=config.get("layer");if(!Set.of("DWD","DWS","ADS").contains(String.valueOf(layer)))throw new IllegalArgumentException("数仓发布节点只支持 DWD、DWS、ADS 逻辑层");if(config.containsKey("table")||config.containsKey("tableName"))throw new IllegalArgumentException("不能在定义中指定物理表名");}
            if("JOIN".equals(node.type())){if(!Set.of("INNER","LEFT","RIGHT","FULL").contains(String.valueOf(config.getOrDefault("joinType","INNER"))))throw new IllegalArgumentException("关联类型无效");validateJoinKeys(config);}
        }
        if(sourceCount<1||outputCount<1)throw new IllegalArgumentException("至少需要一个数据集来源节点和一个数仓发布节点");
        Set<String> edgeIds=new HashSet<>();Map<String,List<String>> forward=new HashMap<>();Map<String,Integer> indegree=new HashMap<>();byKey.keySet().forEach(key->{forward.put(key,new ArrayList<>());indegree.put(key,0);});List<Edge> edges=new ArrayList<>();
        for(Edge raw:definition.edges()){
            if(raw==null||!byKey.containsKey(raw.source())||!byKey.containsKey(raw.target())||raw.source().equals(raw.target()))throw new IllegalArgumentException("连线引用无效或存在自环");
            String type=raw.type()==null?"DATA":raw.type();if(!Set.of("DATA","CONTROL").contains(type))throw new IllegalArgumentException("连线类型无效");
            String sourcePort=port(raw.sourcePort(),"out"),targetPort=port(raw.targetPort(),"in");String id=raw.source()+"|"+sourcePort+"|"+raw.target()+"|"+targetPort;if(!edgeIds.add(id))throw new IllegalArgumentException("存在重复连线");
            edges.add(new Edge(raw.source(),sourcePort,raw.target(),targetPort,type));forward.get(raw.source()).add(raw.target());indegree.compute(raw.target(),(k,v)->v+1);
        }
        // Snapshot degrees before Kahn topo mutates indegree to zero.
        Map<String,Integer> incoming=new HashMap<>(indegree);
        ArrayDeque<String> queue=new ArrayDeque<>();indegree.forEach((key,count)->{if(count==0)queue.add(key);});int visited=0;List<String> topological=new ArrayList<>();while(!queue.isEmpty()){String key=queue.removeFirst();visited++;topological.add(key);for(String target:forward.get(key))if(indegree.compute(target,(k,v)->v-1)==0)queue.add(target);}if(visited!=nodes.size())throw new IllegalArgumentException("流程图存在环路");
        for(Node node:nodes){int in=incoming.getOrDefault(node.key(),0),out=forward.get(node.key()).size();if("SOURCE_DATASET".equals(node.type())&&in!=0)throw new IllegalArgumentException("数据集来源节点不能有输入连线");if(!"SOURCE_DATASET".equals(node.type())&&in==0)throw new IllegalArgumentException("节点缺少输入连线: "+node.key());if("JOIN".equals(node.type())&&in!=2)throw new IllegalArgumentException("关联节点必须且只能连接两路输入");if(!Set.of("SOURCE_DATASET","JOIN").contains(node.type())&&in!=1)throw new IllegalArgumentException("该类型节点必须且只能连接一路输入: "+node.key());if("WAREHOUSE_PUBLISH".equals(node.type())&&out!=0)throw new IllegalArgumentException("数仓发布节点不能继续连接下游节点");}
        Set<String> reachable=new HashSet<>();for(Node node:nodes)if("SOURCE_DATASET".equals(node.type()))reachable.add(node.key());boolean changed=true;while(changed){changed=false;for(Edge edge:edges)if(reachable.contains(edge.source())&&reachable.add(edge.target()))changed=true;}for(Node node:nodes)if(!reachable.contains(node.key()))throw new IllegalArgumentException("存在无法从数据来源到达的节点: "+node.key());
        Set<String> canReachOutput=new HashSet<>();for(Node node:nodes)if("WAREHOUSE_PUBLISH".equals(node.type()))canReachOutput.add(node.key());changed=true;while(changed){changed=false;for(Edge edge:edges)if(canReachOutput.contains(edge.target())&&canReachOutput.add(edge.source()))changed=true;}for(Node node:nodes)if(!canReachOutput.contains(node.key()))throw new IllegalArgumentException("节点没有连接到数仓发布目标: "+node.key());
        nodes=new ArrayList<>(inferSchemas(nodes,edges,byKey,topological));
        Map<String,Object> input=safeObject(definition.inputSchema(),"流程输入结构"),output=safeObject(definition.outputSchema(),"流程输出结构"),params=safeObject(definition.parameters(),"流程参数");if(write(params).length()>8192||containsForbidden(params))throw new IllegalArgumentException("流程参数过大或包含敏感配置");
        nodes.sort(Comparator.comparing(Node::key));edges.sort(Comparator.comparing(Edge::source).thenComparing(Edge::target).thenComparing(Edge::sourcePort).thenComparing(Edge::targetPort));return new Normalized(nodes,edges,input,output,params);
    }

    private Map<String,Object> safeObject(Map<String,Object> value,String label){if(value==null)return Map.of();if(value.size()>128||write(value).length()>16384)throw new IllegalArgumentException(label+"过大");return normalizeMap(value);}
    private boolean containsForbidden(Object value){if(value instanceof Map<?,?> map){for(var e:map.entrySet()){String key=String.valueOf(e.getKey()).toLowerCase();if(key.matches(".*(password|passwd|secret|token|credential|private.?key|sql|script|expression|jdbcurl).*"))return true;if(containsForbidden(e.getValue()))return true;}}else if(value instanceof List<?> list){for(Object item:list)if(containsForbidden(item))return true;}return false;}
    private static Map<String,Object> normalizeMap(Map<String,Object> source){Map<String,Object> result=new TreeMap<>();source.forEach((k,v)->{if(k==null||k.length()>128)throw new IllegalArgumentException("配置字段名无效");if(v instanceof Map<?,?> map){Map<String,Object> nested=new TreeMap<>();map.forEach((nk,nv)->nested.put(String.valueOf(nk),nv));result.put(k,normalizeMap(nested));}else if(v instanceof List<?> list){result.put(k,list.stream().map(item->item instanceof Map<?,?> map?normalizeMap(cast(map)):item instanceof List<?> nested?normalizeList(nested):item).toList());}else result.put(k,v);});return result;}
    private static List<Object> normalizeList(List<?> list){return list.stream().map(item->item instanceof Map<?,?> map?normalizeMap(cast(map)):item instanceof List<?> nested?normalizeList(nested):item).toList();}
    private static Map<String,Object> cast(Map<?,?> map){Map<String,Object> result=new TreeMap<>();map.forEach((k,v)->result.put(String.valueOf(k),v));return result;}
    private static int clamp(Integer v){return Math.max(-100000,Math.min(100000,v==null?0:v));}
    private static void validateJoinKeys(Map<String,Object> config){Object left=config.get("leftKeys"),right=config.get("rightKeys");if(!(left instanceof List<?> l)||!(right instanceof List<?> r)||l.isEmpty()||l.size()!=r.size()||l.size()>16)throw new IllegalArgumentException("关联字段需在左右两侧一一对应，最多 16 组");for(Object key:l)validateFieldName(key);for(Object key:r)validateFieldName(key);}
    private static void validateFieldName(Object raw){if(!(raw instanceof String value)||value.isBlank()||value.length()>128||value.indexOf('\u0000')>=0)throw new IllegalArgumentException("关联字段名无效");}
    private static double decimal(Object raw){try{return raw instanceof Number?new java.math.BigDecimal(String.valueOf(raw)).doubleValue():Double.parseDouble(String.valueOf(raw));}catch(RuntimeException error){return Double.NaN;}}
    private static boolean enabled(Object value){return Boolean.TRUE.equals(value)||(value instanceof Number number&&number.intValue()==1)||"true".equalsIgnoreCase(String.valueOf(value));}
    private List<Node> inferSchemas(List<Node> nodes,List<Edge> edges,Map<String,Node> byKey,List<String> topological){
        Map<String,Map<String,FieldSpec>> outputs=new HashMap<>();Map<String,List<String>> parents=new HashMap<>();
        for(Edge edge:edges)parents.computeIfAbsent(edge.target(),ignored->new ArrayList<>()).add(edge.source());
        Map<String,Node> enriched=new HashMap<>();
        for(String key:topological){Node node=byKey.get(key);List<String> inputs=parents.getOrDefault(key,List.of());Map<String,FieldSpec> output=new LinkedHashMap<>();List<Map<String,Object>> inputDocuments=new ArrayList<>();
            for(String parent:inputs){Map<String,FieldSpec> fields=outputs.get(parent);inputDocuments.add(Map.of("nodeKey",parent,"fields",fieldDocuments(fields)));}
            if("SOURCE_DATASET".equals(node.type())){
                Long datasetId=longValue(node.config().get("datasetId"));DatasetDataPolicy.Scope policy=dataPolicies.resolve(datasetId);
                for(BiDatasetFieldDO field:datasetFields.selectByDatasetId(datasetId))if(!policy.denied().contains(field.getOriginName()))output.put(field.getOriginName(),new FieldSpec(normalizeType(field.getOriginType()),policy.masked().contains(field.getOriginName())));
                if(output.isEmpty())throw new IllegalArgumentException("来源数据集没有可用字段: "+datasetId);
            }else if("JOIN".equals(node.type())){
                if(inputs.size()!=2||new HashSet<>(inputs).size()!=2)throw new IllegalArgumentException("关联节点必须恰好连接两个不同的输入节点");Map<String,FieldSpec> left=outputs.get(inputs.get(0)),right=outputs.get(inputs.get(1));List<?> leftKeys=(List<?>)node.config().get("leftKeys"),rightKeys=(List<?>)node.config().get("rightKeys");
                for(int i=0;i<leftKeys.size();i++){String lf=String.valueOf(leftKeys.get(i)),rf=String.valueOf(rightKeys.get(i));FieldSpec l=left.get(lf),r=right.get(rf);if(l==null)throw new IllegalArgumentException("关联左字段不存在: "+lf);if(r==null)throw new IllegalArgumentException("关联右字段不存在: "+rf);if(l.restricted()||r.restricted())throw new IllegalArgumentException("脱敏或禁止字段不能作为关联键");if(!compatible(l.type(),r.type()))throw new IllegalArgumentException("关联字段类型不兼容: "+lf+" ("+l.type()+") / "+rf+" ("+r.type()+")");}
                for(Map.Entry<String,FieldSpec> field:left.entrySet())output.put(field.getKey(),field.getValue());
                for(Map.Entry<String,FieldSpec> field:right.entrySet()){String name=field.getKey();if(output.containsKey(name))name=inputs.get(1)+"."+name;if(output.putIfAbsent(name,field.getValue())!=null)throw new IllegalArgumentException("关联结果存在重复字段名: "+name);}
            }else{
                if(inputs.isEmpty())throw new IllegalArgumentException("节点缺少输入 schema: "+key);
                Map<String,FieldSpec> input=outputs.get(inputs.get(0));
                if("TRANSFORM".equals(node.type())){
                    Object rawMappings=node.config().get("mappings");if(rawMappings==null)output.putAll(input);else if(rawMappings instanceof List<?> mappings){if(mappings.size()>256)throw new IllegalArgumentException("字段映射最多 256 条");Set<String> targets=new HashSet<>();for(Object item:mappings){if(!(item instanceof Map<?,?> map))throw new IllegalArgumentException("字段映射格式无效");String source=String.valueOf(map.get("source")),target=String.valueOf(map.get("target"));validateFieldName(target);FieldSpec spec=input.get(source);if(spec==null)throw new IllegalArgumentException("映射源字段不存在: "+source);if(spec.restricted())throw new IllegalArgumentException("脱敏或禁止字段不能参与转换");if(!targets.add(target))throw new IllegalArgumentException("映射目标字段重复: "+target);output.put(target,spec);}}else throw new IllegalArgumentException("字段映射必须为数组");
                }else if("FILTER".equals(node.type())){
                    Object rawConditions=node.config().get("conditions");if(rawConditions instanceof List<?> conditions){if(conditions.size()>100)throw new IllegalArgumentException("筛选条件最多 100 条");for(Object item:conditions){if(!(item instanceof Map<?,?> map))throw new IllegalArgumentException("筛选条件格式无效");String field=String.valueOf(map.get("field")),op=String.valueOf(map.get("operator"));FieldSpec spec=input.get(field);if(spec==null)throw new IllegalArgumentException("筛选字段不存在: "+field);if(spec.restricted())throw new IllegalArgumentException("脱敏或禁止字段不能参与筛选");if(!Set.of("EQ","NE","GT","GTE","LT","LTE","CONTAINS","IS_NULL","NOT_NULL").contains(op))throw new IllegalArgumentException("筛选操作符无效");String family=family(spec.type());if("CONTAINS".equals(op)&&!"string".equals(family))throw new IllegalArgumentException("CONTAINS 只能用于文本字段");if(Set.of("GT","GTE","LT","LTE").contains(op)&&!Set.of("number","datetime").contains(family))throw new IllegalArgumentException("大小比较只能用于数值或日期字段");}}else if(rawConditions!=null)throw new IllegalArgumentException("筛选条件必须为数组");output.putAll(input);
                }else if("QUALITY_GATE".equals(node.type())){
                    Long dataset=longValue(node.config().get("ruleSetDatasetId")),ruleSet=longValue(node.config().get("ruleSetId"));String threshold=String.valueOf(node.config().getOrDefault("severity","ERROR"));int thresholdRank=List.of("INFO","WARN","ERROR","FATAL").indexOf(threshold);int enabledCount=0,blockingCount=0;
                    for(Map<String,Object> rule:qualityRules.publishedRules(dataset,ruleSet)){
                        if(!enabled(rule.get("enabled")))continue;enabledCount++;int rank=List.of("INFO","WARN","ERROR","FATAL").indexOf(String.valueOf(rule.getOrDefault("severity","ERROR")));if(rank>=thresholdRank)blockingCount++;String type=String.valueOf(rule.get("type"));if(!Set.of("NOT_NULL","UNIQUE","NULL_RATE_MAX","RANGE","REGEX","FRESHNESS","RECONCILIATION").contains(type))throw new IllegalArgumentException("流程暂不支持该质量规则类型: "+type);
                        String field=String.valueOf(rule.get("field"));FieldSpec spec=input.get(field);if(spec==null)throw new IllegalArgumentException("质量门禁字段不存在于当前流程输入: "+field);if(spec.restricted())throw new IllegalArgumentException("质量门禁不能读取受限字段: "+field);
                    }
                    if(enabledCount==0||blockingCount==0)throw new IllegalArgumentException("质量门禁阻断等级范围内至少需要一条已启用规则");
                    output.putAll(input);
                }else output.putAll(input);
                if("WAREHOUSE_PUBLISH".equals(node.type())){if(output.values().stream().anyMatch(FieldSpec::restricted))throw new IllegalArgumentException("含有脱敏字段的流程结果不能发布到数仓，请在转换节点移除受限字段");String model=String.valueOf(node.config().getOrDefault("modelCode",""));if(!model.matches("[A-Za-z][A-Za-z0-9_]{1,95}"))throw new IllegalArgumentException("数仓模型编码须为 2～96 位字母、数字或下划线");String mode=String.valueOf(node.config().getOrDefault("writeMode","REPLACE"));if(!Set.of("REPLACE","APPEND","UPSERT").contains(mode))throw new IllegalArgumentException("数仓写入模式无效");if("UPSERT".equals(mode)){Object rawKeys=node.config().get("mergeKeys");if(!(rawKeys instanceof List<?> keys)||keys.isEmpty()||keys.size()>16)throw new IllegalArgumentException("UPSERT 写入须提供 1～16 个 mergeKeys");for(Object mk:keys){String field=String.valueOf(mk);if(!output.containsKey(field))throw new IllegalArgumentException("mergeKeys 字段不存在于流程输出: "+field);}}Object partition=node.config().get("partitionField");if(partition!=null&&!output.containsKey(String.valueOf(partition)))throw new IllegalArgumentException("分区字段不存在于流程输出 schema");}
            }
            if(output.size()>2048)throw new IllegalArgumentException("流程推导字段数超过 2048");outputs.put(key,output);
            Map<String,Object> inputSchema=Map.of("inputs",inputDocuments),outputSchema=Map.of("fields",fieldDocuments(output));enriched.put(key,new Node(node.key(),node.type(),node.name(),node.config(),inputSchema,outputSchema,node.x(),node.y()));
        }
        Map<String,Node> resultByKey=enriched;return nodes.stream().map(node->resultByKey.get(node.key())).toList();
    }
    private static List<Map<String,Object>> fieldDocuments(Map<String,FieldSpec> fields){if(fields==null)return List.of();List<Map<String,Object>> docs=new ArrayList<>();fields.forEach((name,spec)->docs.add(Map.of("name",name,"type",spec.type(),"restricted",spec.restricted())));return docs;}
    private static String normalizeType(String raw){return raw==null?"unknown":raw.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+"," ");}
    private static boolean compatible(String left,String right){String l=family(left),r=family(right);return "unknown".equals(l)||"unknown".equals(r)?normalizeType(left).equals(normalizeType(right)):l.equals(r);}
    private static String family(String raw){String t=normalizeType(raw);if(t.matches(".*(tinyint|smallint|mediumint|bigint|integer|int|decimal|numeric|number|float|double|real).*"))return "number";if(t.matches(".*(char|varchar|string|text|clob|enum).*"))return "string";if(t.matches(".*(date|time|year).*"))return "datetime";if(t.matches(".*(bool|bit).*"))return "boolean";if(t.matches(".*(binary|blob).*"))return "binary";return "unknown";}
    private record FieldSpec(String type,boolean restricted){}
    private static Long longValue(Object v){try{return v==null?null:Long.valueOf(String.valueOf(v));}catch(NumberFormatException e){return null;}}
    private static String port(String value,String fallback){String port=value==null||value.isBlank()?fallback:value.trim();if(!port.matches("[A-Za-z][A-Za-z0-9_-]{0,95}"))throw new IllegalArgumentException("节点端口格式无效");return port;}
    private Map<String,Object> requireRevision(Long pipelineId,Long revisionId){resources.require("pipeline",pipelineId,"view");Map<String,Object> row=mapper.revision(pipelineId,revisionId);if(row==null)throw new IllegalArgumentException("流水线版本不存在");return row;}
    private Node nodeFromRow(Map<String,Object> row){return new Node(String.valueOf(row.get("nodeKey")),String.valueOf(row.get("nodeType")),String.valueOf(row.get("name")),asMap(row.get("config")),asMap(row.get("inputSchema")),asMap(row.get("outputSchema")),((Number)row.get("positionX")).intValue(),((Number)row.get("positionY")).intValue());}
    private Edge edgeFromRow(Map<String,Object> row){return new Edge(String.valueOf(row.get("sourceNodeKey")),String.valueOf(row.get("sourcePort")),String.valueOf(row.get("targetNodeKey")),String.valueOf(row.get("targetPort")),String.valueOf(row.get("edgeType")));}
    private Map<String,Object> asMap(Object value){if(value instanceof Map<?,?> map){Map<String,Object> out=new TreeMap<>();map.forEach((k,v)->out.put(String.valueOf(k),v));return out;}if(value instanceof String text&&!text.isBlank())try{return json.readValue(text,Map.class);}catch(RuntimeException ignored){}return Map.of();}
    private String jsonValue(Object value){return value==null?"{}":value instanceof String s?s:write(value);}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(RuntimeException ex){throw new IllegalArgumentException("JSON 定义无法序列化");}}
    private static String limit(String value,int size){if(value==null)return null;String v=value.trim();return v.length()>size?v.substring(0,size):v;}
    private String actor(){return actors.currentActor().username();}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}

    public record Node(String key,String type,String name,Map<String,Object> config,Map<String,Object> inputSchema,Map<String,Object> outputSchema,Integer x,Integer y){}
    public record Edge(String source,String sourcePort,String target,String targetPort,String type){}
    public record Definition(List<Node> nodes,List<Edge> edges,Map<String,Object> inputSchema,Map<String,Object> outputSchema,Map<String,Object> parameters,String remark,Integer expectedVersion){}
    private record Normalized(List<Node> nodes,List<Edge> edges,Map<String,Object> inputSchema,Map<String,Object> outputSchema,Map<String,Object> parameters){}
}
