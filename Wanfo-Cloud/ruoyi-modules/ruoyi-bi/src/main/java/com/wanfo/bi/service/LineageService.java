package com.wanfo.bi.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import com.wanfo.bi.mapper.LineageMapper;
import com.wanfo.bi.security.BiAccessException;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Stores explicit, version-aware lineage relations and immutable edge revisions. */
@Service
public class LineageService
{
    private static final Set<String> LEVELS=Set.of("TABLE","COLUMN");
    private static final Set<String> KINDS=Set.of("DERIVES","CONTAINS","MATERIALIZES","REFERENCES");
    private static final Set<String> METHODS=Set.of("AUTO_SQL","PIPELINE","MANUAL","CATALOG","RESOURCE_BINDING");
    private static final Set<String> COLUMN_TYPES=Set.of("COLUMN","DATASET_FIELD");
    private static final Pattern SENSITIVE=Pattern.compile("(?i)(password|passwd|secret|token|authorization|cookie|credential|jdbc:[^\\\"]+)");
    private final LineageMapper mapper;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final ObjectMapper json;

    public LineageService(LineageMapper mapper,CapabilityProvider capabilities,CurrentActorProvider actors,ObjectMapper json){this.mapper=mapper;this.capabilities=capabilities;this.actors=actors;this.json=json;}

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> save(Map<String,Object> input)
    {
        requireAdmin();Long source=number(input.get("sourceAssetId")),target=number(input.get("targetAssetId"));
        String level=upper(input.get("lineageLevel")),kind=upper(input.get("edgeKind")),method=upper(input.get("sourceMethod"));
        BigDecimal confidence=decimal(input.get("confidence"));
        validate(source,target,level,kind,method,confidence);
        Long sourceVersion=version(input.get("sourceVersionId"),source),targetVersion=version(input.get("targetVersionId"),target);
        if(sourceVersion==null||targetVersion==null)throw new IllegalArgumentException("血缘两端资产必须先有成功的元数据版本");
        String evidence=evidence(input.get("evidence"));String edgeKey=edgeKey(source,target,level,kind);
        Long id=mapper.edgeIdByKey(edgeKey);String actor=actors.currentActor().username();
        boolean created=id==null;
        Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("edgeKey",edgeKey);row.put("sourceAssetId",source);row.put("targetAssetId",target);
        row.put("lineageLevel",level);row.put("edgeKind",kind);row.put("sourceMethod",method);row.put("confidence",confidence);
        row.put("sourceVersionId",sourceVersion);row.put("targetVersionId",targetVersion);row.put("evidenceJson",evidence);row.put("actor",actor);
        if(id==null){if(mapper.insertEdge(row)!=1)throw new IllegalStateException("血缘关系保存失败");id=number(row.get("id"));}
        else {
            Map<String,Object> existing=mapper.edge(id);
            if("REVOKED".equals(existing.get("status")))throw new IllegalArgumentException("已撤销关系不能直接恢复，请建立新的血缘关系");
            if("ACTIVE".equals(existing.get("status"))&&Objects.equals(existing.get("sourceVersionId"),sourceVersion)
                    &&Objects.equals(existing.get("targetVersionId"),targetVersion)&&Objects.equals(existing.get("lineageLevel"),level)
                    &&Objects.equals(existing.get("edgeKind"),kind)&&Objects.equals(existing.get("sourceMethod"),method)
                    &&sameDecimal(existing.get("confidence"),confidence)&&Objects.equals(existing.get("evidenceJson"),evidence))return existing;
            row.put("id",id);if(mapper.updateEdge(row)!=1)throw new IllegalStateException("血缘关系更新失败");
        }
        writeRevision(id,created?"CREATED":"UPDATED",actor);
        return mapper.edge(id);
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> invalidate(Long id,String status)
    {
        requireAdmin();if(!Set.of("STALE","REVOKED").contains(status))throw new IllegalArgumentException("失效状态无效");
        Map<String,Object> current=requireEdge(id);if("REVOKED".equals(current.get("status")))throw new IllegalArgumentException("关系已撤销");
        String actor=actors.currentActor().username();if(mapper.updateStatus(id,status,actor)!=1)throw new IllegalStateException("血缘关系状态未更新");
        writeRevision(id,"STALE".equals(status)?"INVALIDATED":"REVOKED",actor);return mapper.edge(id);
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> recompute(Long id,Map<String,Object> input)
    {
        requireAdmin();Map<String,Object> current=requireEdge(id);
        if("REVOKED".equals(current.get("status")))throw new IllegalArgumentException("已撤销关系不能重算");
        Long source=number(current.get("sourceAssetId")),target=number(current.get("targetAssetId"));
        String method=upper(input.get("sourceMethod"));BigDecimal confidence=decimal(input.get("confidence"));
        if(!METHODS.contains(method)||confidence==null||confidence.compareTo(BigDecimal.ZERO)<0||confidence.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("来源方式或置信度无效");
        Long sourceVersion=mapper.currentVersion(source),targetVersion=mapper.currentVersion(target);
        if(sourceVersion==null||targetVersion==null)throw new IllegalArgumentException("两端资产必须先有成功的元数据版本，才能重算血缘");
        if(!Objects.equals(mapper.versionOwner(sourceVersion),source)||!Objects.equals(mapper.versionOwner(targetVersion),target))throw new BiAccessException(403,"血缘版本与资产不匹配");
        String actor=actors.currentActor().username();Map<String,Object> update=new LinkedHashMap<>();
        update.put("id",id);update.put("lineageLevel",current.get("lineageLevel"));update.put("edgeKind",current.get("edgeKind"));update.put("sourceMethod",method);
        update.put("confidence",confidence);update.put("sourceVersionId",sourceVersion);update.put("targetVersionId",targetVersion);update.put("evidenceJson",evidence(input.get("evidence")));update.put("actor",actor);
        if(mapper.updateEdge(update)!=1)throw new IllegalStateException("血缘关系重算失败");writeRevision(id,"RECOMPUTED",actor);return mapper.edge(id);
    }

    public List<Map<String,Object>> list(Long assetId,boolean activeOnly){requireAdmin();requireAsset(assetId);return mapper.edges(assetId,activeOnly);}
    public List<Map<String,Object>> revisions(Long id){requireAdmin();requireEdge(id);return mapper.revisions(id);}

    private void validate(Long source,Long target,String level,String kind,String method,BigDecimal confidence){
        if(source==null||target==null||source.equals(target)||!LEVELS.contains(level)||!KINDS.contains(kind)||!METHODS.contains(method)||confidence==null||confidence.compareTo(BigDecimal.ZERO)<0||confidence.compareTo(BigDecimal.ONE)>0)
            throw new IllegalArgumentException("请提供不同的有效资产、关系层级/类型、来源方式和 0～1 置信度");
        Map<String,Object> s=requireAsset(source),t=requireAsset(target);
        boolean sc=COLUMN_TYPES.contains(String.valueOf(s.get("assetType"))),tc=COLUMN_TYPES.contains(String.valueOf(t.get("assetType")));
        if("COLUMN".equals(level)?(!sc||!tc):(sc||tc))throw new IllegalArgumentException("血缘层级必须与两端资产类型一致");
    }
    private Map<String,Object> requireAsset(Long id){Map<String,Object> row=mapper.asset(id);if(row==null||!"ACTIVE".equals(row.get("status")))throw new BiAccessException(404,"血缘资产不存在或已失效");return row;}
    private Map<String,Object> requireEdge(Long id){Map<String,Object> edge=mapper.edge(id);if(edge==null)throw new BiAccessException(404,"血缘关系不存在");return edge;}
    private Long version(Object raw,Long assetId){Long id=number(raw);if(id==null)id=mapper.currentVersion(assetId);if(id!=null&&!Objects.equals(mapper.versionOwner(id),assetId))throw new IllegalArgumentException("资产版本必须属于对应血缘端点");return id;}
    private String evidence(Object value){
        if(value==null)return null;
        try{String text=json.writeValueAsString(value);if(text.length()>4000||SENSITIVE.matcher(text).find())throw new IllegalArgumentException("证据摘要不能包含凭据字段且不超过 4000 字符");return text;}
        catch(IllegalArgumentException ex){throw ex;}catch(Exception ex){throw new IllegalArgumentException("证据摘要格式无效");}
    }
    private void writeRevision(Long id,String kind,String actor){Map<String,Object> current=mapper.edge(id);int revision=((Number)current.get("revisionNo")).intValue();
        try{String snapshot=json.writeValueAsString(current);if(mapper.insertRevision(id,revision,snapshot,kind,actor)!=1)throw new IllegalStateException("血缘版本历史写入失败");}
        catch(IllegalStateException ex){throw ex;}catch(Exception ex){throw new IllegalStateException("血缘版本快照生成失败");}}
    private void requireAdmin(){capabilities.require("bi:resource:admin");}
    private static String upper(Object value){return value==null?"":value.toString().trim().toUpperCase(Locale.ROOT);}
    private static Long number(Object value){if(value instanceof Number n)return n.longValue();try{return value==null?null:Long.valueOf(value.toString());}catch(NumberFormatException ex){throw new IllegalArgumentException("资产或版本 ID 无效");}}
    private static BigDecimal decimal(Object value){try{return value==null?null:new BigDecimal(value.toString());}catch(NumberFormatException ex){throw new IllegalArgumentException("置信度格式无效");}}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    public static String edgeKey(Long source,Long target,String level,String kind){return hash(source+"|"+target+"|"+level+"|"+kind);}
    private static boolean sameDecimal(Object raw,BigDecimal expected){try{return raw!=null&&new BigDecimal(raw.toString()).compareTo(expected)==0;}catch(Exception ignored){return false;}}
}
