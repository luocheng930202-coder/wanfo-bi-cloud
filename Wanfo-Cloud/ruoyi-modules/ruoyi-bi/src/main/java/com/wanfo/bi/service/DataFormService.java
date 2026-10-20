package com.wanfo.bi.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.domain.BiDataFormDO;
import com.wanfo.bi.domain.BiDataFormRecordDO;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.domain.form.DataFormField;
import com.wanfo.bi.mapper.BiDataFormMapper;
import com.wanfo.bi.mapper.BiDataFormRecordMapper;
import com.wanfo.bi.mapper.BiDatasetFieldMapper;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.query.CalciteQueryEngine;
import com.wanfo.bi.query.DataFormInsertBuilder;
import com.wanfo.bi.security.*;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataFormService
{
    private final BiDataFormMapper formMapper;
    private final BiDataFormRecordMapper recordMapper;
    private final BiDatasetTableMapper datasetMapper;
    private final BiDatasetFieldMapper fieldMapper;
    private final DataSourceProvider datasourceProvider;
    private final CalciteQueryEngine engine;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider caps;
    private final CurrentActorProvider actors;
    private final ObjectMapper json;
    private final DatasetDataPolicy policies;private final DataFormAuditService audit;private final LiveActorResolver live;

    public DataFormService(BiDataFormMapper formMapper, BiDataFormRecordMapper recordMapper,
                           BiDatasetTableMapper datasetMapper, BiDatasetFieldMapper fieldMapper,
                           DataSourceProvider datasourceProvider, CalciteQueryEngine engine,
                           BiResourceAuthorizer resources, CapabilityProvider caps,
                           CurrentActorProvider actors, ObjectMapper json, DatasetDataPolicy policies, DataFormAuditService audit, LiveActorResolver live)
    {
        this.formMapper = formMapper;
        this.recordMapper = recordMapper;
        this.datasetMapper = datasetMapper;
        this.fieldMapper = fieldMapper;
        this.datasourceProvider = datasourceProvider;
        this.engine = engine;
        this.resources = resources;
        this.caps = caps;
        this.actors = actors;
        this.json = json;this.policies=policies;this.audit=audit;this.live=live;
    }

    public List<Map<String, Object>> list()
    {
        caps.require("bi:form:list");
        List<Map<String, Object>> result = new ArrayList<>();
        for (BiDataFormDO row : formMapper.selectAll())
        {
            if (!canAccessDataset(row.getDatasetId()))
            {
                continue;
            }
            result.add(summary(row));
        }
        return result;
    }

    public Map<String, Object> detail(Long id)
    {
        caps.require("bi:form:list");
        BiDataFormDO row = requireForm(id);
        requireDatasetAccess(row.getDatasetId());
        Map<String, Object> m = summary(row);
        m.put("fields", parseFields(row.getFieldsJson()));
        m.put("remark", row.getRemark());
        return m;
    }

    @Transactional
    public Map<String, Object> create(String name, Long datasetId, List<DataFormField> fields, String remark)
    {
        caps.require("bi:form:add");
        requireDatasetAccess(datasetId);resources.require("dataset",datasetId,"write");
        validateDefinition(name, fields);
        validateWritableFields(datasetId, fields);
        BiDataFormDO row = new BiDataFormDO();
        row.setName(name.trim());
        row.setDatasetId(datasetId);
        row.setFieldsJson(json.writeValueAsString(fields));
        row.setRemark(remark);
        row.setStatus("0");
        row.setCreateBy(actors.currentActor().username());
        formMapper.insert(row);
        return Map.of("id", row.getId());
    }

    @Transactional
    public Map<String, Object> update(Long id, String name, List<DataFormField> fields, String remark, String status)
    {
        caps.require("bi:form:edit");
        BiDataFormDO row = requireForm(id);
        requireDatasetAccess(row.getDatasetId());resources.require("dataset",row.getDatasetId(),"write");
        if (name != null && !name.isBlank())
        {
            row.setName(name.trim());
        }
        if (fields != null)
        {
            validateDefinition(row.getName(), fields);
            validateWritableFields(row.getDatasetId(), fields);
            row.setFieldsJson(json.writeValueAsString(fields));
        }
        if (remark != null)
        {
            row.setRemark(remark);
        }
        if (status != null)
        {
            row.setStatus("1".equals(status) ? "1" : "0");
        }
        row.setUpdateBy(actors.currentActor().username());
        formMapper.update(row);
        return Map.of("updated", true);
    }

    @Transactional
    public Map<String, Object> delete(Long id)
    {
        caps.require("bi:form:remove");
        BiDataFormDO row = requireForm(id);
        requireDatasetAccess(row.getDatasetId());
        resources.require("dataset",row.getDatasetId(),"write");
        formMapper.deleteById(id, actors.currentActor().username());
        return Map.of("deleted", true);
    }

    public Map<String,Object> submit(Long id,Map<String,Object> values,String requestKey) throws Exception {
        var current=live.resolve(actors.currentActor());current.require("bi:form:submit");Object[] result=new Object[1];Exception[] error=new Exception[1];
        ActorScope.run(current.actor(),current.capabilities(),()->{try{result[0]=submitCurrent(id,values,requestKey);}catch(Exception ex){error[0]=ex;}});
        if(error[0]!=null)throw error[0];return (Map<String,Object>)result[0];
    }
    private Map<String,Object> submitCurrent(Long id,Map<String,Object> values,String requestKey) throws Exception {
        caps.require("bi:form:submit");
        if(requestKey==null || !requestKey.matches("[A-Za-z0-9_-]{16,80}"))throw new IllegalArgumentException("请提供有效提交标识，刷新表单后重试");
        var form=requireForm(id);if(!"0".equals(form.getStatus()))throw new IllegalArgumentException("表单已停用");
        requireDatasetAccess(form.getDatasetId());resources.require("dataset",form.getDatasetId(),"write");
        var dataset=requireTableDataset(form.getDatasetId());var source=requireSource(dataset.getDatasourceId());var types=physicalTypes(form.getDatasetId());
        var plan=DataFormInsertBuilder.compile(dataset.getSourceCatalog(),dataset.getSourceTable(),quoteChar(source),parseFields(form.getFieldsJson()),types,values);
        DataFormPolicy.requireWrite(plan.normalized(),types,policies.resolve(form.getDatasetId()));
        var record=new BiDataFormRecordDO();record.setFormId(id);record.setOwnerPrincipal(actors.currentActor().userId());record.setCreateBy(actors.currentActor().username());record.setRequestKey(requestKey);
        record.setRowJson(json.writeValueAsString(plan.normalized()));record.setPayloadHash(SecurityDigest.hash(record.getRowJson()));record.setSuccess("0");record.setSubmissionStatus("pending");
        var reservation=audit.reserve(record);record=reservation.record();if(!reservation.fresh())return outcome(record.getId(),record.getSubmissionStatus());
        boolean committing=false,committed=false;String state="failed";
        try(Connection conn=engine.borrowConnection(source.getSchemaName())){
            conn.setAutoCommit(false);
            try(PreparedStatement st=conn.prepareStatement(plan.sql())){
                st.setQueryTimeout(30);for(int i=0;i<plan.params().size();i++)st.setObject(i+1,plan.params().get(i));st.executeUpdate();
            }catch(Exception ex){try{conn.rollback();}catch(Exception ignored){}throw ex;}
            committing=true;conn.commit();committed=true;
        }catch(Exception ex){state=committed?"succeeded":committing?"uncertain":"failed";}
        if(committed)state="succeeded";
        try{audit.finish(record.getId(),state,"succeeded".equals(state)?null:"failed".equals(state)?"源库写入失败，请检查配置后重新填写":"源库提交结果不确定，请人工核对，勿重复写入");}
        catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(DataFormService.class).warn("Data form submission {} audit completion failed; external state {}",record.getId(),state);return outcome(record.getId(),"pending");}
        if("failed".equals(state))throw new BiAccessException(400,"源库写入失败，失败记录已保留；请检查配置后重新填写");
        return outcome(record.getId(),state);
    }
    private Map<String,Object> outcome(Long id,String status){String label=switch(status){case "succeeded"->"成功";case "failed"->"写入失败";case "uncertain"->"提交结果不确定";default->"待核对";};return Map.of("submitted","succeeded".equals(status),"recordId",id,"status",status,"message","succeeded".equals(status)?"提交成功":"提交已记录，当前状态："+label+"；请核对记录，勿使用新提交标识重复写入");}
    public List<Map<String,Object>> records(Long formId){
        var current=live.resolve(actors.currentActor());current.require("bi:form:list");Object[] result=new Object[1];
        ActorScope.run(current.actor(),current.capabilities(),()->result[0]=recordsCurrent(formId));return (List<Map<String,Object>>)result[0];
    }
    private List<Map<String,Object>> recordsCurrent(Long formId){
        caps.require("bi:form:list");var form=requireForm(formId);requireDatasetAccess(form.getDatasetId());
        var scope=policies.resolve(form.getDatasetId());var types=physicalTypes(form.getDatasetId());List<Map<String,Object>> result=new ArrayList<>();
        for(var row:recordMapper.listVisible(formId,actors.currentActor().userId(),caps.has("bi:resource:admin"))){
            Map<String,Object> values;try{values=json.readValue(row.getRowJson(),Map.class);}catch(Exception ex){continue;}
            if(!DataFormPolicy.matches(values,types,scope))continue;
            Map<String,Object> item=new LinkedHashMap<>();item.put("id",row.getId());item.put("success","1".equals(row.getSuccess()));item.put("status",row.getSubmissionStatus());item.put("reviewNote",row.getReviewNote());item.put("reviewTime",row.getReviewTime());item.put("canReview",caps.has("bi:resource:admin")&&caps.has("bi:form:edit")&&resources.allowed("dataset",form.getDatasetId(),"write"));item.put("errorMsg",row.getErrorMsg());item.put("createBy",row.getCreateBy());item.put("createTime",row.getCreateTime());item.put("row",json.writeValueAsString(DataFormPolicy.visible(values,types,scope)));result.add(item);
        }return result;
    }

    private BiDataFormDO requireForm(Long id)
    {
        BiDataFormDO row = formMapper.selectById(id);
        if (row == null)
        {
            throw new IllegalArgumentException("表单不存在: " + id);
        }
        return row;
    }

    private void validateDefinition(String name, List<DataFormField> fields)
    {
        if (name == null || name.isBlank() || name.trim().length() > 200)
        {
            throw new IllegalArgumentException("表单名称须为 1～200 字符");
        }
        if (fields == null || fields.isEmpty() || fields.size() > 50)
        {
            throw new IllegalArgumentException("请配置 1～50 个填报字段");
        }
    }

    private void validateWritableFields(Long datasetId, List<DataFormField> fields)
    {
        Set<String> allowed = physicalFieldNames(datasetId);
        for (DataFormField f : fields)
        {
            if (!allowed.contains(f.getField()))
            {
                throw new IllegalArgumentException("仅允许写入数据集的物理字段: " + f.getField());
            }
        }
    }

    private Set<String> physicalFieldNames(Long datasetId)
    {
        return fieldMapper.selectByDatasetId(datasetId).stream()
                .filter(f -> !"1".equals(f.getExtFlag()))
                .map(BiDatasetFieldDO::getOriginName)
                .collect(Collectors.toSet());
    }

    private Map<String, String> physicalTypes(Long datasetId)
    {
        Map<String, String> types = new LinkedHashMap<>();
        for (BiDatasetFieldDO f : fieldMapper.selectByDatasetId(datasetId))
        {
            if (!"1".equals(f.getExtFlag()))
            {
                types.put(f.getOriginName(), String.valueOf(f.getOriginType()));
            }
        }
        return types;
    }

    private BiDatasetTableDO requireTableDataset(Long datasetId)
    {
        BiDatasetTableDO ds = datasetMapper.selectById(datasetId);
        if (ds == null || !"table".equals(ds.getType()) || (ds.getModelJson()!=null && !ds.getModelJson().isBlank()))
        {
            throw new IllegalArgumentException("仅支持表数据集填报");
        }
        return ds;
    }

    private BiDatasource requireSource(Long id)
    {
        BiDatasource source = datasourceProvider.getById(id);
        if (source == null || !"0".equals(source.getStatus()))
        {
            throw new BiAccessException(403, "数据源不可用");
        }
        return source;
    }

    private String quoteChar(BiDatasource source) throws Exception
    {
        try (Connection conn = engine.borrowConnection(source.getSchemaName()))
        {
            return conn.getMetaData().getIdentifierQuoteString().trim();
        }
    }

    private List<DataFormField> parseFields(String jsonText)
    {
        try
        {
            return List.of(json.readValue(jsonText, DataFormField[].class));
        }
        catch (Exception ex)
        {
            throw new IllegalArgumentException("表单字段配置无效");
        }
    }

    private Map<String, Object> summary(BiDataFormDO row)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("name", row.getName());
        m.put("datasetId", row.getDatasetId());
        m.put("status", row.getStatus());
        m.put("createBy", row.getCreateBy());
        m.put("createTime", row.getCreateTime());
        return m;
    }

    private void requireDatasetAccess(Long datasetId)
    {
        resources.require("dataset", datasetId, "view");
        resources.require("dataset", datasetId, "query");
    }

    private boolean canAccessDataset(Long datasetId)
    {
        return resources.allowed("dataset", datasetId, "view")
                && resources.allowed("dataset", datasetId, "query");
    }
}
