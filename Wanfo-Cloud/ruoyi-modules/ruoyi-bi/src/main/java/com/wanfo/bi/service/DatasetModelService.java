package com.wanfo.bi.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import tools.jackson.databind.ObjectMapper;
import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.domain.dataset.DatasetJoinModel;
import com.wanfo.bi.mapper.BiDatasetFieldMapper;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.query.CalcFormulaCompiler;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetModelService
{
    private static final String RIGHT_PREFIX = "r_";

    private final BiDatasetTableMapper datasetMapper;
    private final BiDatasetFieldMapper fieldMapper;
    private final DataSourceProvider datasourceProvider;
    private final CatalogService catalog;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final ObjectMapper objectMapper;

    public DatasetModelService(BiDatasetTableMapper datasetMapper,
                               BiDatasetFieldMapper fieldMapper,
                               DataSourceProvider datasourceProvider,
                               CatalogService catalog,
                               BiResourceAuthorizer resources,
                               CapabilityProvider capabilities,
                               ObjectMapper objectMapper)
    {
        this.datasetMapper = datasetMapper;
        this.fieldMapper = fieldMapper;
        this.datasourceProvider = datasourceProvider;
        this.catalog = catalog;
        this.resources = resources;
        this.capabilities = capabilities;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> joinModel(Long datasetId)
    {
        BiDatasetTableDO row = requireTableDataset(datasetId);
        requireModelWrite(row);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("datasetId", datasetId);
        out.put("modelJson", row.getModelJson());
        if (row.getModelJson() != null && !row.getModelJson().isBlank())
        {
            try
            {
                out.put("join", objectMapper.readValue(row.getModelJson(), DatasetJoinModel.class));
                DatasetJoinModel saved = (DatasetJoinModel) out.get("join");
                saved.setJoins(saved.normalizedJoins());
            }
            catch (Exception ignored)
            {
                out.put("join", null);
            }
        }
        return out;
    }

    @Transactional
    public Map<String, Object> applyJoin(Long datasetId, DatasetJoinModel model, String operator) throws Exception
    {
        BiDatasetTableDO row = requireTableDataset(datasetId);
        requireModelWrite(row);
        if (model == null || model.normalizedJoins().isEmpty()) { throw new IllegalArgumentException("请至少配置一条关联"); }
        BiDatasource source = requireSource(row.getDatasourceId());
        List<BiDatasetFieldDO> existing = fieldMapper.selectByDatasetId(datasetId);
        DatasetJoinModel previous = row.getModelJson() == null || row.getModelJson().isBlank()
                ? new DatasetJoinModel() : objectMapper.readValue(row.getModelJson(), DatasetJoinModel.class);
        Set<String> previousGenerated = generatedFieldNames(previous);
        List<String> leftFields = existing.stream()
                .filter(f -> !"1".equals(f.getExtFlag()))
                .filter(f -> !previousGenerated.contains(f.getOriginName()))
                .filter(f -> !(previous.normalizedJoins().isEmpty() && f.getOriginName().startsWith(RIGHT_PREFIX)))
                .map(BiDatasetFieldDO::getOriginName)
                .toList();
        if (leftFields.isEmpty())
        { throw new IllegalArgumentException("数据集没有可用的左表字段"); }
        List<DatasetJoinModel.JoinStep> joins = model.normalizedJoins();
        if (joins.size() > 8) throw new IllegalArgumentException("单个数据集最多配置 8 条关联");
        String sql = catalog.savedPlan(source.getSchemaName(), row.getSourceCatalog(), row.getSourceTable(), leftFields).sql();
        Set<String> available = new LinkedHashSet<>(leftFields);
        List<Map<String,Object>> addedColumns = new ArrayList<>();
        Set<String> newGenerated = new LinkedHashSet<>();
        for (int i = 0; i < joins.size(); i++) {
            DatasetJoinModel.JoinStep step = joins.get(i);
            if (step == null || !available.contains(step.getLeftKey())) throw new IllegalArgumentException("第 " + (i + 1) + " 条关联的左键不可用");
            String prefix = DatasetJoinModel.fieldPrefix(i + 1);
            CatalogService.JoinStepPlan plan = catalog.savedJoinStep(source.getSchemaName(), row.getSourceCatalog(), sql,
                    step.getRightTable(), step.getJoinType(), step.getRightKey(), step.getRightFields(), step.getLeftKey(), prefix, i + 1);
            sql = plan.sql();
            for (Map<String,Object> col : plan.rightColumns()) {
                String raw = String.valueOf(col.get("name")), name = prefix + raw;
                if (!newGenerated.add(name) || available.contains(name)) throw new IllegalArgumentException("关联字段名重复：" + name);
                available.add(name);
                Map<String,Object> mapped = new LinkedHashMap<>(col); mapped.put("datasetName", name); mapped.put("joinIndex", i + 1);
                addedColumns.add(mapped);
            }
        }
        if (available.size() > 200) throw new IllegalArgumentException("关联后的数据集字段不能超过 200 个");
        model.setJoins(joins);
        if (!joins.isEmpty()) {
            var first = joins.get(0); model.setJoinType(first.getJoinType()); model.setRightTable(first.getRightTable());
            model.setLeftKey(first.getLeftKey()); model.setRightKey(first.getRightKey()); model.setRightFields(first.getRightFields());
        }
        removeGeneratedFields(existing, previousGenerated);
        int order = existing.stream().map(BiDatasetFieldDO::getOrderNum).filter(n -> n != null).mapToInt(Integer::intValue).max().orElse(-1) + 1;
        List<BiDatasetFieldDO> added = new ArrayList<>();
        for (Map<String, Object> col : addedColumns)
        {
            String raw = String.valueOf(col.get("name")), datasetName = String.valueOf(col.get("datasetName"));
            BiDatasetFieldDO f = new BiDatasetFieldDO();
            f.setDatasetId(datasetId);
            f.setOriginName(datasetName);
            f.setOriginType(String.valueOf(col.get("type")));
            String show = FieldDisplayLabels.catalogShowName(col);
            if (show.equals(raw)) {
                show = datasetName;
            }
            f.setShowName(show);
            f.setDimFlag("metric".equals(col.get("suggestAs")) ? "0" : "1");
            f.setExtFlag("0");
            f.setGroupName("关联 " + col.get("joinIndex"));
            f.setOrderNum(order++);
            f.setStatus("0");
            fieldMapper.insert(f);
            added.add(f);
        }
        datasetMapper.updateUnionAndModel(datasetId, sql, objectMapper.writeValueAsString(model), operator);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unionSql", sql);
        result.put("rightFieldCount", added.size());
        result.put("joinCount", joins.size());
        return result;
    }

    @Transactional
    public Map<String, Object> clearJoin(Long datasetId, String operator) throws Exception
    {
        BiDatasetTableDO row = requireTableDataset(datasetId);
        requireModelWrite(row);
        if (row.getModelJson() == null || row.getModelJson().isBlank())
        { return Map.of("cleared", false); }
        BiDatasource source = requireSource(row.getDatasourceId());
        List<BiDatasetFieldDO> existing = fieldMapper.selectByDatasetId(datasetId);
        DatasetJoinModel previous = objectMapper.readValue(row.getModelJson(), DatasetJoinModel.class);
        removeGeneratedFields(existing, generatedFieldNames(previous));
        List<String> leftFields = fieldMapper.selectByDatasetId(datasetId).stream()
                .filter(f -> !"1".equals(f.getExtFlag()))
                .map(BiDatasetFieldDO::getOriginName)
                .toList();
        CatalogService.TablePlan plan = catalog.savedPlan(source.getSchemaName(), row.getSourceCatalog(),
                row.getSourceTable(), leftFields);
        datasetMapper.updateUnionAndModel(datasetId, plan.sql(), null, operator);
        return Map.of("cleared", true, "unionSql", plan.sql());
    }

    @Transactional
    public Map<String, Object> addCalculatedField(Long datasetId, String originName, String showName,
                                                  String formula, String dimFlag, String operator)
    {
        BiDatasetTableDO row = requireTableDataset(datasetId);
        requireModelWrite(row);
        validateCalcName(originName);
        List<BiDatasetFieldDO> fields = fieldMapper.selectByDatasetId(datasetId);
        if (fields.stream().anyMatch(f -> f.getOriginName().equalsIgnoreCase(originName)))
        { throw new IllegalArgumentException("字段名已存在: " + originName); }
        Set<String> physical = fields.stream()
                .map(BiDatasetFieldDO::getOriginName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        CalcFormulaCompiler.compile(formula, physical);
        int order = fields.stream().map(BiDatasetFieldDO::getOrderNum).filter(n -> n != null).mapToInt(Integer::intValue).max().orElse(-1) + 1;
        BiDatasetFieldDO f = new BiDatasetFieldDO();
        f.setDatasetId(datasetId);
        f.setOriginName(originName);
        f.setOriginType(com.wanfo.bi.query.CalcFieldTypes.infer(formula, fields));
        f.setShowName(showName == null || showName.isBlank() ? originName : showName.trim());
        f.setDimFlag("0".equals(dimFlag) ? "0" : "1");
        f.setExtFlag("1");
        f.setExtFormula(formula.trim());
        f.setGroupName("计算字段");
        f.setOrderNum(order);
        f.setStatus("0");
        fieldMapper.insert(f);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", f.getId());
        result.put("originName", f.getOriginName());
        return result;
    }

    @Transactional
    public Map<String, Object> removeCalculatedField(Long datasetId, Long fieldId, String operator)
    {
        BiDatasetTableDO row = requireTableDataset(datasetId);
        requireModelWrite(row);
        BiDatasetFieldDO field = fieldMapper.selectById(fieldId);
        if (field == null || !datasetId.equals(field.getDatasetId()) || !"1".equals(field.getExtFlag()))
        { throw new IllegalArgumentException("计算字段不存在"); }
        List<BiDatasetFieldDO> allFields=fieldMapper.selectByDatasetId(datasetId);Set<String> allowed=allFields.stream().map(BiDatasetFieldDO::getOriginName).collect(Collectors.toSet());
        for(BiDatasetFieldDO dependent:allFields)if("1".equals(dependent.getExtFlag())&&!dependent.getId().equals(fieldId)&&CalcFormulaCompiler.references(dependent.getExtFormula(),allowed).contains(field.getOriginName()))throw new IllegalArgumentException("该字段被后续计算步骤引用，请先移除依赖步骤");
        fieldMapper.deleteById(fieldId);
        row.setUpdateBy(operator);
        datasetMapper.update(row);
        return Map.of("removed", true);
    }

    @Transactional
    public Map<String,Object> orderCalculatedFields(Long datasetId,List<Long> orderedIds,String operator) {
        BiDatasetTableDO row=requireTableDataset(datasetId);requireModelWrite(row);
        List<BiDatasetFieldDO> all=fieldMapper.selectByDatasetId(datasetId);
        List<BiDatasetFieldDO> calcs=all.stream().filter(f->"1".equals(f.getExtFlag())).toList();
        if(orderedIds==null||orderedIds.size()!=calcs.size()||orderedIds.stream().anyMatch(Objects::isNull)||new LinkedHashSet<>(orderedIds).size()!=calcs.size())throw new IllegalArgumentException("请提交完整且不重复的清洗步骤顺序");
        Map<Long,BiDatasetFieldDO> byId=calcs.stream().collect(Collectors.toMap(BiDatasetFieldDO::getId,f->f));
        Set<String> available=all.stream().filter(f->!"1".equals(f.getExtFlag())).map(BiDatasetFieldDO::getOriginName).collect(Collectors.toCollection(LinkedHashSet::new));
        List<BiDatasetFieldDO> ordered=new ArrayList<>();
        for(Long id:orderedIds){BiDatasetFieldDO step=byId.get(id);if(step==null)throw new IllegalArgumentException("清洗步骤不存在");CalcFormulaCompiler.compile(step.getExtFormula(),available);available.add(step.getOriginName());ordered.add(step);}
        int order=0;for(BiDatasetFieldDO step:ordered)fieldMapper.updateCalculatedOrder(datasetId,step.getId(),order++);
        row.setUpdateBy(operator);datasetMapper.update(row);
        return Map.of("orderedIds",orderedIds,"replayedOnQuery",true);
    }

    private void removeGeneratedFields(List<BiDatasetFieldDO> existing, Set<String> generated)
    {
        for (BiDatasetFieldDO f : existing)
        {
            if (!"1".equals(f.getExtFlag()) && (generated.contains(f.getOriginName())
                    || generated.isEmpty() && f.getOriginName().startsWith(RIGHT_PREFIX)))
            { fieldMapper.deleteById(f.getId()); }
        }
    }

    private Set<String> generatedFieldNames(DatasetJoinModel model) {
        Set<String> names = new LinkedHashSet<>();
        List<DatasetJoinModel.JoinStep> joins = model == null ? List.of() : model.normalizedJoins();
        for (int i = 0; i < joins.size(); i++) {
            String prefix = DatasetJoinModel.fieldPrefix(i + 1);
            List<String> fields = joins.get(i).getRightFields();
            if (fields != null) fields.forEach(field -> names.add(prefix + field));
        }
        return names;
    }

    private void validateCalcName(String originName)
    {
        if (originName == null || !originName.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"))
        { throw new IllegalArgumentException("计算字段名须为字母数字下划线"); }
        if (originName.startsWith(RIGHT_PREFIX))
        { throw new IllegalArgumentException("计算字段名不能以 " + RIGHT_PREFIX + " 开头"); }
    }

    private BiDatasetTableDO requireTableDataset(Long datasetId)
    {
        resources.require("dataset", datasetId, "view");
        BiDatasetTableDO row = datasetMapper.selectById(datasetId);
        if (row == null)
        { throw new IllegalArgumentException("数据集不存在: " + datasetId); }
        if (!"table".equals(row.getType()))
        { throw new IllegalArgumentException("仅表数据集支持建模扩展"); }
        return row;
    }

    private void requireModelWrite(BiDatasetTableDO row)
    {
        capabilities.require("bi:datasource:model");
        long gid = row.getGroupId() == null ? 0L : row.getGroupId();
        if (gid != 0)
        { resources.require("dataset-group", gid, "edit"); }
    }

    private BiDatasource requireSource(Long datasourceId)
    {
        BiDatasource source = datasourceProvider.getById(datasourceId);
        if (source == null || !"0".equals(source.getStatus()))
        { throw new IllegalArgumentException("数据源不可用"); }
        return source;
    }
}
