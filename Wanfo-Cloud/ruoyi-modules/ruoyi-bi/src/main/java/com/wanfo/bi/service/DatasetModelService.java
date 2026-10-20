package com.wanfo.bi.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        if (model == null || model.getRightTable() == null || model.getRightTable().isBlank())
        { throw new IllegalArgumentException("请配置关联表"); }
        BiDatasource source = requireSource(row.getDatasourceId());
        List<BiDatasetFieldDO> existing = fieldMapper.selectByDatasetId(datasetId);
        List<String> leftFields = existing.stream()
                .filter(f -> !"1".equals(f.getExtFlag()))
                .filter(f -> !f.getOriginName().startsWith(RIGHT_PREFIX))
                .map(BiDatasetFieldDO::getOriginName)
                .toList();
        if (leftFields.isEmpty())
        { throw new IllegalArgumentException("数据集没有可用的左表字段"); }
        CatalogService.JoinPlan plan = catalog.planJoin(source.getSchemaName(), row.getSourceCatalog(),
                row.getSourceTable(), leftFields, model.getJoinType(), model.getRightTable(),
                model.getRightFields(), model.getLeftKey(), model.getRightKey());
        removeRightFields(existing);
        int order = existing.stream().map(BiDatasetFieldDO::getOrderNum).filter(n -> n != null).mapToInt(Integer::intValue).max().orElse(-1) + 1;
        List<BiDatasetFieldDO> added = new ArrayList<>();
        for (Map<String, Object> col : plan.rightColumns())
        {
            String raw = String.valueOf(col.get("name"));
            BiDatasetFieldDO f = new BiDatasetFieldDO();
            f.setDatasetId(datasetId);
            f.setOriginName(RIGHT_PREFIX + raw);
            f.setOriginType(String.valueOf(col.get("type")));
            String show = FieldDisplayLabels.catalogShowName(col);
            if (show.equals(raw)) {
                show = RIGHT_PREFIX + raw;
            }
            f.setShowName(show);
            f.setDimFlag("metric".equals(col.get("suggestAs")) ? "0" : "1");
            f.setExtFlag("0");
            f.setGroupName("关联");
            f.setOrderNum(order++);
            f.setStatus("0");
            fieldMapper.insert(f);
            added.add(f);
        }
        datasetMapper.updateUnionAndModel(datasetId, plan.sql(), objectMapper.writeValueAsString(model), operator);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unionSql", plan.sql());
        result.put("rightFieldCount", added.size());
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
        removeRightFields(existing);
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
                .filter(f -> !"1".equals(f.getExtFlag()))
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
        fieldMapper.deleteById(fieldId);
        row.setUpdateBy(operator);
        datasetMapper.update(row);
        return Map.of("removed", true);
    }

    private void removeRightFields(List<BiDatasetFieldDO> existing)
    {
        for (BiDatasetFieldDO f : existing)
        {
            if (!"1".equals(f.getExtFlag()) && f.getOriginName().startsWith(RIGHT_PREFIX))
            { fieldMapper.deleteById(f.getId()); }
        }
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
