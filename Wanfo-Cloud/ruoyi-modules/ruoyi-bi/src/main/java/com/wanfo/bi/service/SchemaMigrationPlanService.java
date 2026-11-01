package com.wanfo.bi.service;

import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.mapper.BiDatasetFieldMapper;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.mapper.SchemaChangeMapper;
import com.wanfo.bi.mapper.SchemaMigrationMapper;
import com.wanfo.bi.query.CalcFormulaCompiler;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Reviewed dataset field migrations for source schema changes. */
@Service
public class SchemaMigrationPlanService
{
    public record FieldMapping(Long datasetId, String sourceField, String targetField) { }

    private final SchemaMigrationMapper mapper;
    private final SchemaChangeMapper changes;
    private final BiDatasetFieldMapper fields;
    private final BiDatasetTableMapper datasets;
    private final CatalogService catalog;
    private final DataSourceProvider sources;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final CatalogAuditService audit;
    private final OpsImpactPackService impactPack;
    private final FieldStandardService fieldStandards;
    private final ChangeTicketService changeTickets;
    private final ObjectMapper json;

    public SchemaMigrationPlanService(SchemaMigrationMapper mapper, SchemaChangeMapper changes,
            BiDatasetFieldMapper fields, BiDatasetTableMapper datasets, CatalogService catalog,
            DataSourceProvider sources, BiResourceAuthorizer resources, CapabilityProvider capabilities,
            CurrentActorProvider actors, CatalogAuditService audit, OpsImpactPackService impactPack,
            FieldStandardService fieldStandards, ChangeTicketService changeTickets, ObjectMapper json)
    {
        this.mapper=mapper; this.changes=changes; this.fields=fields; this.datasets=datasets; this.catalog=catalog;
        this.sources=sources; this.resources=resources; this.capabilities=capabilities; this.actors=actors;
        this.impactPack=impactPack; this.fieldStandards=fieldStandards; this.changeTickets=changeTickets;
        this.audit=audit; this.json=json;
    }

    public List<Map<String,Object>> list(Long eventId) { requireAdmin(); requireEvent(eventId); return mapper.plans(eventId); }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> create(Long eventId, List<FieldMapping> mappings)
    {
        requireAdmin(); Map<String,Object> event=requireEvent(eventId);
        if(mappings==null || mappings.isEmpty() || mappings.size()>500) throw new IllegalArgumentException("请提供 1～500 个明确的字段映射");
        Long assetId=number(event.get("assetId")); Map<String,Object> sourceAsset=mapper.asset(assetId);
        if(sourceAsset==null||!"TABLE".equals(sourceAsset.get("assetType"))||!"CONNECTOR".equals(sourceAsset.get("sourceKind"))) throw new IllegalArgumentException("当前结构事件不是数据库表资产，无法应用 BI 字段迁移");
        Map<String,Object> eventDetails=parse(event.get("detailsJson"));
        Map<String,Object> fieldChanges=asMap(eventDetails.get("fields"));
        Set<String> removed=lowerStrings(fieldChanges.get("removed"));
        Set<String> changed=lowerStrings(fieldChanges.get("changed"));
        List<String> blockers=new ArrayList<>(); List<Map<String,Object>> steps=new ArrayList<>();
        Map<Long,Map<String,Object>> beforeDatasets=new LinkedHashMap<>(); Set<String> unique=new LinkedHashSet<>(); int order=0;
        for(FieldMapping mapping:mappings)
        {
            if(mapping==null||mapping.datasetId()==null||mapping.sourceField()==null||mapping.sourceField().isBlank()) { blockers.add("字段映射缺少数据集或源字段"); continue; }
            Long datasetId=mapping.datasetId(); String sourceField=mapping.sourceField().trim();
            if(!removed.contains(sourceField.toLowerCase(java.util.Locale.ROOT))) blockers.add("源字段不属于本次删除的字段："+sourceField);
            BiDatasetTableDO ds=datasets.selectById(datasetId); if(ds==null) { blockers.add("数据集不存在："+datasetId); continue; }
            resources.require("dataset",datasetId,"edit");
            if(!"table".equals(ds.getType()) || ds.getModelJson()!=null&&!ds.getModelJson().isBlank()) { blockers.add("仅支持单表物理数据集："+ds.getName()); continue; }
            if(!java.util.Objects.equals(ds.getDatasourceId(),number(sourceAsset.get("sourceId"))) || !java.util.Objects.equals(ds.getSourceTable(),String.valueOf(sourceAsset.get("name")))) { blockers.add("数据集来源与结构变化资产不匹配："+ds.getName()); continue; }
            BiDatasetFieldDO field=fields.selectByDatasetId(datasetId).stream().filter(f->sourceField.equalsIgnoreCase(physical(f))).findFirst().orElse(null);
            if(field==null||!"0".equals(field.getExtFlag())) { blockers.add("找不到受影响的物理字段："+ds.getName()+" / "+sourceField); continue; }
            if(changed.contains(sourceField.toLowerCase(java.util.Locale.ROOT))) blockers.add("字段类型或元数据已变化，需要单独调整数据集定义："+sourceField);
            String target=mapping.targetField()==null?null:mapping.targetField().trim(); String operation=target==null||target.isBlank()?"DROP":"RENAME";
            if("RENAME".equals(operation))
            {
                if(!unique.add(datasetId+":"+target.toLowerCase(java.util.Locale.ROOT))) blockers.add("目标字段重复："+target);
                if(!targetFieldExists(assetId,target)) blockers.add("目标字段不在当前源表中："+target);
                String targetType=metadataType(assetId,target);
                if(targetType!=null&&!normalizeType(targetType).equals(normalizeType(field.getOriginType()))) blockers.add("目标字段类型与原字段不一致，需人工调整："+sourceField+" → "+target);
                boolean occupied=fields.selectByDatasetId(datasetId).stream().anyMatch(f->!field.getId().equals(f.getId())&&target.equalsIgnoreCase(f.getOriginName())&&"0".equals(f.getDelFlag()));
                if(occupied) blockers.add("目标名称已被数据集逻辑字段占用："+target);
            }
            else
            {
                Map<String,Object> deps=mapper.dependencyCounts(datasetId);
                if(deps.values().stream().filter(Number.class::isInstance).map(Number.class::cast).anyMatch(n->n.longValue()>0)) blockers.add("删除字段存在图表、权限、質量、表单或流程依赖，不能自动删除："+ds.getName()+" / "+sourceField);
                Set<String> names=new LinkedHashSet<>(); fields.selectByDatasetId(datasetId).forEach(f->names.add(f.getOriginName()));
                for(BiDatasetFieldDO f:fields.selectByDatasetId(datasetId)) if("1".equals(f.getExtFlag())) try { if(CalcFormulaCompiler.references(f.getExtFormula(),names).contains(f.getOriginName())) { /* keep formula parse validated below */ } if(CalcFormulaCompiler.references(f.getExtFormula(),names).contains(field.getOriginName())) blockers.add("删除字段被计算字段引用："+sourceField); } catch(RuntimeException invalid) { blockers.add("计算字段依赖无法安全解析："+f.getShowName()); }
            }
            Map<String,Object> datasetSnapshot=beforeDatasets.computeIfAbsent(datasetId,id->{Map<String,Object> row=mapper.dataset(id); return row==null?Map.of():new LinkedHashMap<>(row);});
            Map<String,Object> before=fieldSnapshot(field); Map<String,Object> after=new LinkedHashMap<>(before);
            after.put("sourceName",target); after.put("originType",field.getOriginType()); after.put("delFlag","DROP".equals(operation)?"2":"0");
            steps.add(new LinkedHashMap<>(Map.of("order",++order,"datasetId",datasetId,"fieldId",field.getId(),"operation",operation,"sourceField",sourceField,"targetField",target==null?"":target,"targetType",field.getOriginType()==null?"":field.getOriginType(),"before",write(before),"after",write(after))));
        }
        Map<String,Object> definition=Map.of("eventId",eventId,"mappings",mappings);
        String status=blockers.isEmpty()?"DRAFT":"BLOCKED";
        Map<String,Object> command=new LinkedHashMap<>(); command.put("eventId",eventId); command.put("planNo",mapper.nextPlanNo(eventId)); command.put("status",status); command.put("definition",write(definition)); command.put("blockers",write(blockers)); command.put("beforeSnapshot",write(Map.of("datasets",beforeDatasets))); command.put("actor",actors.currentActor().username());
        mapper.insertPlan(command); Long planId=number(command.get("id"));
        for(Map<String,Object> step:steps) mapper.insertStep(planId,(Integer)step.get("order"),(Long)step.get("datasetId"),(Long)step.get("fieldId"),(String)step.get("operation"),(String)step.get("sourceField"),(String)step.get("targetField"),(String)step.get("targetType"),(String)step.get("before"),(String)step.get("after"));
        revision(planId,"CREATED"); audit.record("SCHEMA_MIGRATION_PLAN_CREATE","SCHEMA_MIGRATION_PLAN",planId,null,Map.of("status",status,"steps",steps.size(),"blockers",blockers.size()));
        return detail(planId);
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> approve(Long id) { requireAdmin(); var p=required(id); if(!"DRAFT".equals(p.get("status"))) throw new IllegalStateException("只有无阻断项的草案可以审批"); String actor=actors.currentActor().username(); if(mapper.approve(id,actor,"APPROVED")!=1) throw new IllegalStateException("审批状态已变化，请刷新"); revision(id,"APPROVED"); audit.record("SCHEMA_MIGRATION_PLAN_APPROVE","SCHEMA_MIGRATION_PLAN",id,null,Map.of()); return detail(id); }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> execute(Long id) throws Exception {
        return execute(id, false, null);
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> execute(Long id, boolean overrideBlocking) throws Exception {
        return execute(id, overrideBlocking, null);
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> execute(Long id, boolean overrideBlocking, String overrideTicket) throws Exception
    {
        requireAdmin(); var p=mapper.lockPlan(id); if(p==null) throw new IllegalArgumentException("迁移计划不存在"); if(!"APPROVED".equals(p.get("status"))) throw new IllegalStateException("迁移计划需先审批");
        Map<String,Object> event=mapper.event(number(p.get("eventId")));
        if(event==null) throw new IllegalStateException("结构变化事件已不存在，请重新生成迁移计划");
        Long assetId=number(event.get("assetId"));
        if (overrideBlocking) {
            String ticket = overrideTicket == null ? "" : overrideTicket.trim();
            if (ticket.isBlank()) throw new IllegalArgumentException("overrideBlocking 须填写已审批变更工单号");
            Long ticketId;
            try { ticketId = Long.valueOf(ticket); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("变更工单号无效"); }
            changeTickets.assertApprovedOverrideTicket(ticketId, assetId);
            mapper.recordBlockingOverride(id, ticket, actors.currentActor().username());
        }
        impactPack.assertWritable(assetId, overrideBlocking);
        if (overrideBlocking) {
            audit.record("SCHEMA_MIGRATION_BLOCKING_OVERRIDE", "SCHEMA_MIGRATION_PLAN", id, assetId,
                    Map.of("severity", "BLOCKING", "override", true, "ticket", overrideTicket));
        }
        fieldStandards.assertEnforceGate(assetId);
        Map<String,Object> asset=mapper.asset(assetId);
        if(asset==null||!java.util.Objects.equals(number(asset.get("currentVersionId")),number(event.get("currentVersionId"))))
            throw new IllegalStateException("源表结构已在审批后发生变化，请重新生成并审批迁移计划");
        Set<String> removed=lowerStrings(parse(event.get("detailsJson")).get("fields") instanceof Map<?,?> d?d.get("removed"):null);
        List<Map<String,Object>> steps=mapper.steps(id); Map<Long,List<Map<String,Object>>> byDataset=new LinkedHashMap<>();
        for(Map<String,Object> step:steps)
        {
            Long ds=number(step.get("datasetId")); resources.require("dataset",ds,"edit");
            BiDatasetFieldDO f=fields.selectById(number(step.get("datasetFieldId")));
            Map<String,Object> before=parse(step.get("beforeJson"));
            if(f==null||!String.valueOf(step.get("sourceField")).equalsIgnoreCase(physical(f))||!snapshotMatches(f,before))
                throw new IllegalStateException("数据集字段自计划审批后已变化，请重新生成并审批迁移计划");
            String sourceField=String.valueOf(step.get("sourceField"));
            if(!removed.contains(sourceField.toLowerCase(java.util.Locale.ROOT))) throw new IllegalStateException("结构变化事件已不再包含待迁移字段，请重新生成计划");
            if("RENAME".equals(step.get("operation")))
            {
                String target=String.valueOf(step.get("targetField")); String currentType=metadataType(assetId,target);
                if(currentType==null||!normalizeType(currentType).equals(normalizeType(String.valueOf(step.get("targetType")))))
                    throw new IllegalStateException("目标源字段已不存在或类型已变化，请重新生成并审批迁移计划");
            }
            else if("DROP".equals(step.get("operation")))
            {
                Map<String,Object> dependencies=mapper.dependencyCounts(ds);
                if(dependencies.values().stream().filter(Number.class::isInstance).map(Number.class::cast).anyMatch(n->n.longValue()>0))
                    throw new IllegalStateException("字段依赖在审批后发生变化，拒绝删除，请重新生成迁移计划");
            }
            byDataset.computeIfAbsent(ds,k->new ArrayList<>()).add(step);
        }
        for(var entry:byDataset.entrySet())
        {
            Long datasetId=entry.getKey(); BiDatasetTableDO ds=datasets.selectById(datasetId);
            for(Map<String,Object> step:entry.getValue()) { Map<String,Object> after=parse(String.valueOf(step.get("afterJson"))); String source=(String)step.get("sourceField"); if("DROP".equals(step.get("operation"))) fields.applySourceDrop(datasetId,number(step.get("datasetFieldId")),source); else fields.applySourceMapping(datasetId,number(step.get("datasetFieldId")),source,String.valueOf(step.get("targetField")),String.valueOf(step.get("targetType"))); mapper.updateStepStatus(number(step.get("id")),"APPLIED"); }
            List<BiDatasetFieldDO> current=fields.selectByDatasetId(datasetId); Map<String,String> physicalMap=new LinkedHashMap<>(); List<String> logical=new ArrayList<>(); for(BiDatasetFieldDO f:current) if("0".equals(f.getExtFlag())&& !"2".equals(f.getDelFlag())) { logical.add(f.getOriginName()); if(f.getSourceName()!=null&&!f.getSourceName().isBlank()) physicalMap.put(f.getOriginName(),f.getSourceName()); }
            BiDatasource source=sources.getById(ds.getDatasourceId()); if(source==null) throw new IllegalStateException("数据源不可用"); String sql=catalog.savedPlan(source.getSchemaName(),ds.getSourceCatalog(),ds.getSourceTable(),logical,physicalMap).sql(); datasets.updateUnionAndModel(datasetId,sql,null,actors.currentActor().username());
        }
        if(mapper.markExecuted(id,actors.currentActor().username(),"EXECUTED")!=1) throw new IllegalStateException("执行状态更新失败"); revision(id,"EXECUTED"); audit.record("SCHEMA_MIGRATION_PLAN_EXECUTE","SCHEMA_MIGRATION_PLAN",id,null,Map.of("steps",steps.size())); return detail(id);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void recordFailure(Long id,String message) { mapper.markFailed(id,message==null?"迁移执行失败":message.substring(0,Math.min(900,message.length()))); revision(id,"FAILED"); }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> rollback(Long id)
    {
        requireAdmin(); Map<String,Object> p=mapper.lockPlan(id); if(p==null||!"EXECUTED".equals(p.get("status"))) throw new IllegalStateException("仅已执行计划可回滚");
        for(Map<String,Object> step:mapper.steps(id)) { BiDatasetFieldDO f=fields.selectByIdIncludingDeleted(number(step.get("datasetFieldId"))); if(f==null) throw new IllegalStateException("字段记录不存在，无法安全回滚"); Map<String,Object> after=parse(String.valueOf(step.get("afterJson"))); if(!java.util.Objects.equals(f.getSourceName(),after.get("sourceName"))||!java.util.Objects.equals(f.getDelFlag(),after.get("delFlag"))) throw new IllegalStateException("字段在执行后又被修改，拒绝覆盖回滚"); Map<String,Object> before=parse(String.valueOf(step.get("beforeJson"))); fields.restoreMigrationField(number(step.get("datasetId")),f.getId(),(String)before.get("sourceName"),(String)before.get("originType"),(String)before.get("delFlag")); mapper.updateStepStatus(number(step.get("id")),"ROLLED_BACK"); }
        Map<String,Object> snap=parse(String.valueOf(p.get("beforeSnapshotJson"))); Map<String,Object> oldDatasets=asMap(snap.get("datasets")); for(var entry:oldDatasets.entrySet()) { Long dsId; try{dsId=Long.valueOf(entry.getKey());}catch(Exception ignored){continue;} Map<String,Object> old=asMap(entry.getValue()); datasets.updateUnionAndModel(dsId,(String)old.get("unionSql"),null,actors.currentActor().username()); }
        mapper.markRolledBack(id,actors.currentActor().username()); revision(id,"ROLLED_BACK"); audit.record("SCHEMA_MIGRATION_PLAN_ROLLBACK","SCHEMA_MIGRATION_PLAN",id,null,Map.of()); return detail(id);
    }

    public Map<String,Object> detail(Long id) { requireAdmin(); Map<String,Object> p=required(id); Map<String,Object> result=new LinkedHashMap<>(p); result.put("steps",mapper.steps(id)); result.put("blockers",parse(p.get("blockersJson"))); result.put("definition",parse(p.get("definitionJson"))); return result; }
    public List<Map<String,Object>> revisions(Long id) { requireAdmin(); required(id); return mapper.revisions(id); }
    private void requireAdmin(){capabilities.require("bi:resource:admin");}
    private Map<String,Object> requireEvent(Long id){Map<String,Object> event=mapper.event(id); if(event==null)throw new IllegalArgumentException("结构变化事件不存在"); return event;}
    private Map<String,Object> required(Long id){Map<String,Object> p=mapper.plan(id); if(p==null)throw new IllegalArgumentException("迁移计划不存在"); return p;}
    private void revision(Long id,String kind){mapper.insertRevision(id,mapper.nextRevision(id),write(detailSnapshot(id)),kind,actors.currentActor().username());}
    private Map<String,Object> detailSnapshot(Long id){Map<String,Object> p=new LinkedHashMap<>(required(id)); p.put("steps",mapper.steps(id)); return p;}
    private Map<String,Object> fieldSnapshot(BiDatasetFieldDO f){Map<String,Object> m=new LinkedHashMap<>();m.put("sourceName",f.getSourceName());m.put("originType",f.getOriginType());m.put("delFlag",f.getDelFlag());m.put("originName",f.getOriginName());return m;}
    private boolean snapshotMatches(BiDatasetFieldDO f,Map<String,Object> expected){return java.util.Objects.equals(f.getSourceName(),expected.get("sourceName"))&&java.util.Objects.equals(f.getOriginType(),expected.get("originType"))&&java.util.Objects.equals(f.getDelFlag(),expected.get("delFlag"))&&java.util.Objects.equals(f.getOriginName(),expected.get("originName"));}
    private String physical(BiDatasetFieldDO f){return f.getSourceName()==null||f.getSourceName().isBlank()?f.getOriginName():f.getSourceName();}
    private boolean targetFieldExists(Long assetId,String field){return metadataType(assetId,field)!=null;}
    private String metadataType(Long assetId,String field){Object versionId=requireEventAssetVersion(assetId);if(versionId==null)return null;Map<String,Object> v=mapper.version(number(versionId));if(v==null)return null;Map<String,Object> meta=parse(v.get("metadataJson"));Object raw=meta.get("columns");if(raw instanceof List<?> list)for(Object item:list)if(item instanceof Map<?,?> m&&field.equalsIgnoreCase(String.valueOf(m.get("name"))))return m.get("type")==null?null:String.valueOf(m.get("type"));return null;}
    private String normalizeType(String value){return value==null?"":value.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+"," ").trim();}
    private Object requireEventAssetVersion(Long assetId){Map<String,Object> asset=mapper.asset(assetId);return asset==null?null:asset.get("currentVersionId");}
    private Map<String,Object> parse(Object value){if(value==null)return new LinkedHashMap<>();try{return json.readValue(String.valueOf(value),Map.class);}catch(Exception e){return new LinkedHashMap<>();}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("迁移计划序列化失败",e);}}
    private Map<String,Object> asMap(Object value){if(value instanceof Map<?,?> map){Map<String,Object> result=new LinkedHashMap<>();map.forEach((k,v)->result.put(String.valueOf(k),v));return result;}return new LinkedHashMap<>();}
    private Set<String> lowerStrings(Object value){Set<String> result=new LinkedHashSet<>();if(value instanceof List<?> list)for(Object item:list)result.add(String.valueOf(item).toLowerCase(java.util.Locale.ROOT));return result;}
    private Long number(Object value){return value instanceof Number n?n.longValue():value==null?null:parseLong(value);}
    private Long parseLong(Object value){try{return Long.valueOf(String.valueOf(value));}catch(Exception e){return null;}}
}
