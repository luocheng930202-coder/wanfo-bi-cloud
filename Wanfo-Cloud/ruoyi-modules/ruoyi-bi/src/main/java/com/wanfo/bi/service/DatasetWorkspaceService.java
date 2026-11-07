package com.wanfo.bi.service;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.stream.Collectors;
import com.wanfo.bi.domain.*;
import com.wanfo.bi.domain.dataset.*;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.datasource.*;
import com.wanfo.bi.query.*;
import com.wanfo.bi.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Loads and atomically updates the original dataset; never creates a replacement dataset. */
@Service
public class DatasetWorkspaceService {
    public record Draft(BiDatasetTableDO dataset,DatasetJoinModel model,List<DatasetService.FieldSelection> fields,
                        Map<String,List<String>> parameterValues,String revision,String impactToken) {}
    public record Impact(List<String> blockers,List<String> warnings,List<String> changedFields,List<Map<String,Object>> references,String token) {}
    private record Plan(BiDatasetTableDO dataset,List<BiDatasetFieldDO> fields,DatasetJoinModel model) {}
    private final BiDatasetTableMapper datasets;
    private final BiDatasetFieldMapper fields;
    private final DatasetWorkspaceMapper writes;
    private final SchemaMigrationMapper dependencies;
    private final DataSourceProvider sources;
    private final CatalogService catalog;
    private final DatasetSqlNodeService nodes;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider caps;
    private final UserQueryCache cache;
    private final ObjectMapper json=new ObjectMapper();
    @Autowired(required = false)
    private LineageRebuildTrigger lineageRebuild;

    public DatasetWorkspaceService(BiDatasetTableMapper datasets,BiDatasetFieldMapper fields,DatasetWorkspaceMapper writes,
            SchemaMigrationMapper dependencies,DataSourceProvider sources,CatalogService catalog,DatasetSqlNodeService nodes,
            BiResourceAuthorizer resources,CapabilityProvider caps,UserQueryCache cache){
        this.datasets=datasets;this.fields=fields;this.writes=writes;this.dependencies=dependencies;this.sources=sources;
        this.catalog=catalog;this.nodes=nodes;this.resources=resources;this.caps=caps;this.cache=cache;
    }
    private BiDatasetTableDO required(Long id){
        caps.require("bi:datasource:model");resources.require("dataset",id,"edit");
        var row=datasets.selectById(id);if(row==null)throw new IllegalArgumentException("数据集不存在");
        if(!Set.of("table","sql").contains(row.getType()))throw new IllegalArgumentException("该数据集类型不支持画布编辑");
        if("sql".equals(row.getType()) || DatasetSqlNodeService.isSqlNode(row))resources.requireAdministrator();
        if(row.getGroupId()!=null && row.getGroupId()!=0)resources.require("dataset-group",row.getGroupId(),"edit");
        return row;
    }
    private DatasetJoinModel model(BiDatasetTableDO row){
        return row.getModelJson()==null || row.getModelJson().isBlank()?new DatasetJoinModel():json.readValue(row.getModelJson(),DatasetJoinModel.class);
    }
    private String hash(Object value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception error){throw new IllegalStateException("无法生成编辑校验版本",error);}
    }
    private String revision(BiDatasetTableDO row,List<BiDatasetFieldDO> definitions){return hash(Arrays.asList(row,definitions));}

    public Map<String,Object> load(Long id){
        var row=required(id);var stored=fields.selectByDatasetId(id);var model=model(row);
        if("sql".equals(row.getType()))model.setSourceSql(row.getSqlText());
        if(stored.stream().anyMatch(f->f.getSourceName()!=null && !f.getSourceName().isBlank() && !f.getSourceName().equals(f.getOriginName())))
            throw new IllegalArgumentException("该数据集含结构迁移字段映射，请通过结构迁移维护，暂不支持画布覆盖编辑");
        // Reconcile model-backed rules with later legacy additions and metadata edits.
        var operations=new HashMap<String,DatasetDerivedField>();
        if(model.getDerivedFields()!=null)for(var operation:model.getDerivedFields())operations.put(operation.originName(),operation);
        var restored=new ArrayList<DatasetDerivedField>();
        for(var field:stored)if("1".equals(field.getExtFlag())){
            var operation=operations.get(field.getOriginName());
            if(operation==null || "calc".equals(operation.kind()))restored.add(new DatasetDerivedField("calc",field.getOriginName(),field.getShowName(),field.getDimFlag(),field.getExtFormula(),null,null,null));
            else restored.add(new DatasetDerivedField(operation.kind(),field.getOriginName(),field.getShowName(),field.getDimFlag(),operation.formula(),operation.sourceField(),operation.groups(),operation.otherLabel()));
        }
        model.setDerivedFields(restored);
        model.setJoins(model.normalizedJoins());
        var generated=new HashSet<String>();for(int i=0;i<model.normalizedJoins().size();i++)for(String name:model.normalizedJoins().get(i).getRightFields())generated.add(DatasetJoinModel.fieldPrefix(i+1)+name);
        return Map.of("dataset",row,"model",model,"fields",stored,"baseFields",stored.stream().filter(f->!"1".equals(f.getExtFlag()) && !generated.contains(f.getOriginName())).toList(),"revision",revision(row,stored));
    }

    public Impact check(Long id,Draft draft) throws Exception {
        var before=required(id);var previous=fields.selectByDatasetId(id);assertRevision(before,previous,draft);
        return impact(before,previous,build(id,draft));
    }
    private void assertRevision(BiDatasetTableDO row,List<BiDatasetFieldDO> previous,Draft draft){
        if(draft==null || draft.revision()==null || !revision(row,previous).equals(draft.revision()))throw new IllegalArgumentException("数据集已被修改，请重新打开编辑页后再保存");
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> save(Long id,Draft draft,String operator) throws Exception {
        required(id);if(writes.lock(id)==null)throw new IllegalArgumentException("数据集不存在");
        var before=required(id);var previous=fields.selectByDatasetId(id);assertRevision(before,previous,draft);
        var plan=build(id,draft);var impact=impact(before,previous,plan);
        if(!impact.blockers().isEmpty())throw new IllegalArgumentException(String.join("；",impact.blockers()));
        if(!impact.token().equals(draft.impactToken()))throw new IllegalArgumentException("定义或引用关系已变化，请重新检查影响后确认保存");
        var existing=previous.stream().collect(Collectors.toMap(BiDatasetFieldDO::getOriginName,f->f));
        Set<String> names=new HashSet<>();
        for(var next:plan.fields()){
            names.add(next.getOriginName());next.setDatasetId(id);var old=existing.get(next.getOriginName());
            if(old==null)fields.insert(next);
            else{next.setId(old.getId());if(writes.updateFieldDefinition(next)!=1)throw new IllegalStateException("字段已变化，请重新加载");}
        }
        for(var old:previous)if(!names.contains(old.getOriginName()))fields.deleteById(old.getId());
        var row=plan.dataset();row.setId(id);row.setUpdateBy(operator);
        if(writes.updateDefinition(row)!=1)throw new IllegalStateException("数据集保存失败");
        // Clear only after commit, so a failed transaction cannot publish a new cache generation.
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){cache.clear(id);}});
        else cache.clear(id);
        if(lineageRebuild!=null)lineageRebuild.afterChange();
        return Map.of("id",id,"fieldCount",plan.fields().size());
    }

    @SuppressWarnings("unchecked")
    private Plan build(Long id,Draft draft) throws Exception {
        var row=draft.dataset();if(row==null || row.getName()==null || row.getName().isBlank() || row.getName().trim().length()>200)throw new IllegalArgumentException("请填写有效的数据集名称");
        row.setName(row.getName().trim());row.setGroupId(row.getGroupId()==null?0L:row.getGroupId());
        if(row.getRemark()!=null && row.getRemark().length()>500)throw new IllegalArgumentException("说明不能超过500字");
        if(row.getGroupId()!=0)resources.require("dataset-group",row.getGroupId(),"edit");
        var old=datasets.selectById(id);
        if((!Objects.equals(old.getName(),row.getName()) || !Objects.equals(old.getGroupId(),row.getGroupId())) && datasets.countByName(row.getGroupId(),row.getName())>0)throw new IllegalArgumentException("同目录下已存在同名数据集");
        var source=sources.getById(row.getDatasourceId());if(source==null || !"0".equals(source.getStatus()))throw new IllegalArgumentException("数据源不可用");
        var selections=draft.fields();if(selections==null || selections.isEmpty() || selections.size()>200)throw new IllegalArgumentException("请选择1～200个主表字段");
        var model=draft.model()==null?new DatasetJoinModel():draft.model();var result=new ArrayList<BiDatasetFieldDO>();
        String base;
        List<Map<String,Object>> metadata;
        if(model.getSourceSql()!=null){
            resources.requireAdministrator();
            var parameters=new DatasetJoinModel();parameters.setSqlParameters(model.getSqlParameters());
            var probe=nodes.inspect(source,model.getSourceSql(),row.getSourceCatalog(),null,parameters,1,draft.parameterValues(),true);
            metadata=(List<Map<String,Object>>)probe.get("fieldMetadata");model.setSourceSql(String.valueOf(probe.get("normalizedSql")));
            base=DatasetSqlNodeService.project(model.getSourceSql(),selections.stream().map(DatasetService.FieldSelection::name).toList());row.setSourceTable(null);
            if(row.getWarehouseModelId()!=null)throw new IllegalArgumentException("SQL节点不能绑定物理数仓模型");
        }else{
            var plan=catalog.plan(source.getSchemaName(),row.getSourceCatalog(),row.getSourceTable(),selections.stream().map(DatasetService.FieldSelection::name).toList());
            base=plan.sql();metadata=plan.columns();row.setSourceCatalog(plan.catalog());row.setSourceTable(plan.table());
        }
        Set<String> names=new HashSet<>();
        for(var selection:selections){
            var column=metadata.stream().filter(c->Objects.equals(c.get("name"),selection.name())).findFirst().orElseThrow(()->new IllegalArgumentException("字段不存在，请重新解析："+selection.name()));
            addField(result,names,selection.name(),String.valueOf(column.get("type")),selection.showName(),selection.dimFlag());
        }
        var joins=model.normalizedJoins();if(joins.size()>8)throw new IllegalArgumentException("最多支持8条关联");
        for(int i=0;i<joins.size();i++){
            var join=joins.get(i);if(join==null || !names.contains(join.getLeftKey()))throw new IllegalArgumentException("关联左键不可用");
            var step=catalog.savedJoinStep(source.getSchemaName(),row.getSourceCatalog(),base,join.getRightTable(),join.getJoinType(),join.getRightKey(),join.getRightFields(),join.getLeftKey(),DatasetJoinModel.fieldPrefix(i+1),i+1);
            base=step.sql();for(var col:step.rightColumns())addField(result,names,DatasetJoinModel.fieldPrefix(i+1)+col.get("name"),String.valueOf(col.get("type")),null,null);
        }
        // Join output labels and roles are not editable in the canvas; retain their existing settings.
        var priorFields=fields.selectByDatasetId(id).stream().collect(Collectors.toMap(BiDatasetFieldDO::getOriginName,f->f));
        for(var f:result)if(f.getOriginName().matches("r[0-9]*_.*") && priorFields.containsKey(f.getOriginName())){var previous=priorFields.get(f.getOriginName());f.setShowName(previous.getShowName());f.setDimFlag(previous.getDimFlag());f.setGroupName(previous.getGroupName());}
        model.setJoins(joins);
        result.addAll(DatasetDerivedFields.compile(result,model.getDerivedFields()));
        if(result.size()>200)throw new IllegalArgumentException("数据集最多支持200个字段");
        // Validate the complete query with joins and derived fields, including empty-result metadata.
        var previewModel=new DatasetJoinModel();previewModel.setDerivedFields(model.getDerivedFields());previewModel.setSqlParameters(model.getSqlParameters());
        nodes.inspect(source,base,row.getSourceCatalog(),null,previewModel,1,draft.parameterValues(),true);
        row.setType("table");row.setUnionSql(base);row.setModelJson(json.writeValueAsString(model));
        return new Plan(row,result,model);
    }
    private void addField(List<BiDatasetFieldDO> output,Set<String> names,String name,String type,String label,String role){
        if(name==null || name.isBlank() || !names.add(name))throw new IllegalArgumentException("字段名称为空或重复");
        if(label!=null && label.trim().length()>200)throw new IllegalArgumentException("字段显示名称过长");
        if(role!=null && !Set.of("0","1").contains(role))throw new IllegalArgumentException("字段角色无效");
        var field=new BiDatasetFieldDO();field.setOriginName(name);field.setOriginType(type);field.setShowName(label==null || label.isBlank()?name:label.trim());
        field.setDimFlag(role==null?(type.toUpperCase(Locale.ROOT).matches(".*(INT|DECIMAL|NUMERIC|NUMBER|FLOAT|DOUBLE|REAL).*" )?"0":"1"):role);
        field.setExtFlag("0");field.setGroupName("0".equals(field.getDimFlag())?"度量":"维度");field.setOrderNum(output.size());field.setStatus("0");output.add(field);
    }

    private Impact impact(BiDatasetTableDO before,List<BiDatasetFieldDO> previous,Plan plan){
        var blockers=new ArrayList<String>();var warnings=new ArrayList<String>();var references=new ArrayList<Map<String,Object>>();
        var next=plan.fields().stream().collect(Collectors.toMap(BiDatasetFieldDO::getOriginName,f->f));
        var changed=previous.stream().filter(f->!next.containsKey(f.getOriginName()) || !type(f.getOriginType()).equals(type(next.get(f.getOriginName()).getOriginType()))).map(BiDatasetFieldDO::getOriginName).toList();
        var charts=writes.charts(before.getId());var dashboards=writes.dashboards(before.getId());
        for(var chart:charts){
            var affected=changed.stream().filter(name->uses(chart.getDefinitionJson(),name,previous)).toList();
            if(!affected.isEmpty())blockers.add("图表 #"+chart.getId()+" 引用的字段不能删除或改变类型："+String.join("、",affected));
            references.add(reference("chart",chart.getId(),chart.getName(),affected));
        }
        for(var dashboard:dashboards){
            Long id=((Number)dashboard.get("id")).longValue();references.add(reference("dashboard",id,String.valueOf(dashboard.get("name")),changed));
            if(!changed.isEmpty())blockers.add("看板 #"+id+" 含草稿或已发布绑定，请先处理其字段依赖："+String.join("、",changed));
        }
        var counts=new TreeMap<String,Object>();var primaryCounts=dependencies.dependencyCounts(before.getId());if(primaryCounts!=null)counts.putAll(primaryCounts);var extraCounts=writes.additionalDependencies(before.getId());if(extraCounts!=null)counts.putAll(extraCounts);
        if(!changed.isEmpty() && counts!=null)for(var count:counts.entrySet())if(!Set.of("charts","dashboards").contains(count.getKey()) && count.getValue() instanceof Number n && n.longValue()>0)blockers.add("存在 "+count.getKey()+" 依赖，暂不能删除或变更字段类型："+String.join("、",changed));
        boolean semantic=!Objects.equals(before.getDatasourceId(),plan.dataset().getDatasourceId()) || !Objects.equals(before.getSourceCatalog(),plan.dataset().getSourceCatalog()) || !Objects.equals(before.getSourceTable(),plan.dataset().getSourceTable()) || !Objects.equals(before.getUnionSql(),plan.dataset().getUnionSql()) || !Objects.equals(before.getModelJson(),plan.dataset().getModelJson());
        if((before.getWarehouseModelId()!=null || counts.get("warehouseScopes") instanceof Number scopeCount && scopeCount.longValue()>0) && (!Objects.equals(before.getDatasourceId(),plan.dataset().getDatasourceId()) || !Objects.equals(before.getSourceCatalog(),plan.dataset().getSourceCatalog()) || !Objects.equals(before.getSourceTable(),plan.dataset().getSourceTable())))blockers.add("已绑定数仓消费模型的数据集不能直接更换来源，请先通过数仓范围流程处理");
        if(semantic)warnings.add("查询、关联、参数或计算规则发生变化，可能改变统计口径；请预览确认后保存。");
        if(!references.isEmpty() && semantic)warnings.add("该数据集被 "+references.size()+" 个图表/看板引用，保存后下游查询将使用新定义。");
        if(!references.isEmpty() && plan.model().getSqlParameters()!=null)for(var p:plan.model().getSqlParameters())if(p.required() && (!"ALL".equals(p.defaultScope()) || p.defaultValues()==null || p.defaultValues().isEmpty()))blockers.add("已被引用的数据集不能引入缺少查询默认值的必填参数："+p.name());
        // Non-field dependencies also carry warehouse scope restrictions.
        if(!Objects.equals(before.getWarehouseModelId(),plan.dataset().getWarehouseModelId()))blockers.add("编辑画布暂不支持更换数仓绑定，请通过数仓范围流程维护");
        if(previous.stream().anyMatch(f->f.getSourceName()!=null && !f.getSourceName().isBlank() && !f.getSourceName().equals(f.getOriginName())))blockers.add("存在结构迁移字段映射，不能用画布定义覆盖");
        var token=hash(Arrays.asList(revision(before,previous),plan.dataset(),plan.fields(),charts,dashboards,counts,blockers,warnings));
        return new Impact(blockers,warnings,changed,references,token);
    }
    private Map<String,Object> reference(String type,Long id,String name,List<String> affected){
        boolean visible=resources.allowed(type,id,"view");
        return Map.of("type",type,"id",id,"name",visible?name:"无权查看名称的引用资源","visible",visible,"fields",affected);
    }
    private String type(String value){return Objects.toString(value,"").toUpperCase(Locale.ROOT).replaceAll("\\s+NOT NULL","").replaceAll("\\s+","");}
    private boolean uses(String definition,String name,List<BiDatasetFieldDO> previous){
        if(definition==null || definition.isBlank())return true;
        try{return contains(json.readTree(definition),name,previous.stream().filter(f->name.equals(f.getOriginName())).map(BiDatasetFieldDO::getId).findFirst().orElse(null));}
        catch(Exception error){return true;}
    }
    private boolean contains(tools.jackson.databind.JsonNode node,String name,Long id){
        if(node.isTextual()){String text=node.asText();return text.equals(name) || java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}_])"+java.util.regex.Pattern.quote(name)+"(?![\\p{L}\\p{N}_])").matcher(text).find() || text.contains("["+name+"]") || text.contains("\""+name+"\"") || id!=null && text.equals(String.valueOf(id));}
        if(node.isNumber())return id!=null && node.asLong()==id;
        if(node.isContainer())for(var child:node)if(contains(child,name,id))return true;
        return false;
    }
}
