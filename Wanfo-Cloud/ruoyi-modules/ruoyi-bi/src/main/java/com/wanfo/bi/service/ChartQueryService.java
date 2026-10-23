package com.wanfo.bi.service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.mapper.BiDatasetFieldMapper;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.query.CalciteQueryEngine;
import com.wanfo.bi.query.ChartQueryCompiler;
import com.wanfo.bi.query.QueryResult;
import org.springframework.stereotype.Service;
import com.wanfo.bi.security.*;

/**
 * 图表取数服务（S6-T02）。
 *
 * <p>调用链：
 * <pre>
 *   QueryModel
 *     -> DatasetDataPolicy             解析主体命中的固定行条件及列限制
 *     -> restrictedBase()              在聚合输入范围应用规则
 *     -> ChartQueryCompiler.compile()  校验允许的维度/度量/过滤/排序
 *     -> Calcite 参数化执行
 * </pre>
 *
 * @author zhengliang
 */
@Service
public class ChartQueryService
{
    private final BiDatasetTableMapper datasetMapper;
    private final BiDatasetFieldMapper fieldMapper;
    private final ChartQueryCompiler compiler;
    private final DataSourceProvider datasourceProvider;
    private final CalciteQueryEngine engine;
    private final DatasetDataPolicy policies;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider caps;
    private com.wanfo.bi.query.QueryGovernor governor;
    @org.springframework.beans.factory.annotation.Autowired
    public void configureGovernor(com.wanfo.bi.query.QueryGovernor governor){this.governor=governor;}

    public ChartQueryService(BiDatasetTableMapper datasetMapper,
                             BiDatasetFieldMapper fieldMapper,
                             ChartQueryCompiler compiler,
                             DataSourceProvider datasourceProvider,
                             CalciteQueryEngine engine, DatasetDataPolicy policies, BiResourceAuthorizer resources, CapabilityProvider caps)
    {
        this.datasetMapper = datasetMapper;
        this.fieldMapper = fieldMapper;
        this.compiler = compiler;
        this.datasourceProvider = datasourceProvider;
        this.engine = engine;this.policies=policies;this.resources=resources;this.caps=caps;
    }

    /**
     * 执行图表查询。
     *
     * @param model 查询模型
     * @return 图表数据：列名 + 行 + 实际 SQL + 耗时
     */
    public Map<String, Object> query(QueryModel model) throws Exception
    {
        if(model==null) throw new IllegalArgumentException("查询模型不能为空");
        requireQuery(model.getDatasetId());
        return execute(model);
    }

    // Only DashboardService calls this with a model resolved from the current published snapshot.
    Map<String,Object> queryForDashboard(QueryModel model) throws Exception {
        if(model==null || model.getDatasetId()==null) throw new IllegalArgumentException("发布查询定义无效");
        requireConsumption(model.getDatasetId());
        try {
            var result=execute(model);
            result.remove("executedSql");result.remove("params");result.remove("datasetName");
            return result;
        } catch(BiAccessException ex) {throw ex;}
        catch(IllegalArgumentException ex) {throw new BiAccessException(403,"看板字段或数据规则已改变，请联系管理员");}
        catch(com.wanfo.bi.query.QueryGovernanceException ex){throw ex;}
        catch(Exception ex) {
            org.slf4j.LoggerFactory.getLogger(ChartQueryService.class).warn("Dashboard dataset {} query failed",model.getDatasetId(),ex);
            throw new BiAccessException(500,"看板取数失败，请检查数据源或联系管理员");
        }
    }

    private Map<String,Object> execute(QueryModel model) throws Exception {
        BiDatasetTableDO ds = datasetMapper.selectById(model.getDatasetId());
        if (ds == null)
        {
            throw new IllegalArgumentException("数据集不存在: " + model.getDatasetId());
        }
        if(model.getPivot()!=null && !"table".equals(ds.getType()))throw new IllegalArgumentException("完整透视汇总仅支持表数据集");
        if(model.getTimeComparison()!=null && !"table".equals(ds.getType()))throw new IllegalArgumentException("时间比较仅支持表数据集");
        if (datasourceProvider.getById(ds.getDatasourceId()) == null)
        {
            throw new IllegalArgumentException("数据集绑定的数据源不存在或已停用");
        }

        List<BiDatasetFieldDO> fields = fieldMapper.selectByDatasetId(model.getDatasetId());
        if (fields.isEmpty())
        {
            throw new IllegalArgumentException("数据集没有字段，无法查询");
        }
        Map<String,String> valid = new LinkedHashMap<>();
        for (BiDatasetFieldDO f : fields)
        {
            valid.put(f.getOriginName(),String.valueOf(f.getOriginType()));
        }

        String unionSql = "sql".equals(ds.getType())
                ? stripTrailingSemicolon(ds.getSqlText())
                : ds.getUnionSql();
        if (!"sql".equals(ds.getType()))
        { unionSql = com.wanfo.bi.query.DatasetQueryBaseBuilder.expand(unionSql, fields); }

        var scope=policies.resolve(model.getDatasetId());
        var restricted=restrictedBase(unionSql,fields,scope);
        scope.denied().forEach(valid::remove);scope.masked().forEach(valid::remove);
        var pivot=model.getPivot()==null?null:com.wanfo.bi.query.PivotQueryPlan.compile(model,restricted.sql(),valid,compiler,restricted.params());
        ChartQueryCompiler.Compiled compiled = pivot==null?compiler.compile(model, restricted.sql(), valid):pivot.query();
        List<Object> params=new ArrayList<>();if(pivot==null)params.addAll(restricted.params());params.addAll(compiled.params);
        String sql = compiled.sql;

        long start = System.currentTimeMillis();
        QueryResult qr;
        List<Map<String,Object>> prior=List.of();
        // A table dataset depends only on its bound source; unrelated offline sources
        // must not prevent its query. Administrator SQL datasets may span sources.
        var selectedSources="table".equals(ds.getType())?List.of(datasourceProvider.getById(ds.getDatasourceId())):datasourceProvider.listEnabled();
        var run=governor==null?null:governor.enter(selectedSources.stream().map(com.wanfo.bi.datasource.BiDatasource::getId).toList());
        try (run; Connection conn = engine.open(selectedSources))
        {
            qr = engine.queryWithParams(conn, sql, params, model.getTopN()!=null?10001:pivot!=null?pivot.maxRows():model.getLimit() == null ? ChartQueryCompiler.DEFAULT_LIMIT : model.getLimit());
            if(model.getTimeComparison()!=null && qr.getRows().size()<(model.getLimit()==null?ChartQueryCompiler.DEFAULT_LIMIT:model.getLimit())){
                var baseline=com.wanfo.bi.query.TimeComparison.baseline(model,com.wanfo.bi.query.TimeGrain.calendarRows(model,qr.getRows()));
                if(baseline!=null){var reference=compiler.compile(baseline,restricted.sql(),valid);var referenceParams=new ArrayList<>(restricted.params());referenceParams.addAll(reference.params);prior=com.wanfo.bi.query.TimeGrain.calendarRows(baseline,engine.queryWithParams(conn,reference.sql,referenceParams,1001).getRows());}
            }
            if(run!=null)run.check();
        }
        catch(Exception | AssertionError failure){if(run!=null)throw run.failure(failure);throw failure;}

        Map<String, Object> result = new LinkedHashMap<>();
        if(model.getTopN()!=null){var top=com.wanfo.bi.query.TopNQuery.decode(model,qr.getColumns(),qr.getRows());qr.setColumns(top.columns());qr.setRows(top.rows());result.put("topN",top.metadata());}
        result.put("columns", pivot==null?qr.getColumns():qr.getColumns().stream().filter(column->!column.equals(pivot.rowDepthKey()) && !column.equals(pivot.colDepthKey())).toList());
        var pivotResult=pivot==null?null:com.wanfo.bi.query.PivotQueryPlan.decode(model,pivot,qr.getRows());
        if(pivotResult!=null)result.put("pivotTotals",pivotResult.totals());
        var filled=com.wanfo.bi.query.TimeGapFill.apply(model,com.wanfo.bi.query.TimeGrain.calendarRows(model,pivotResult==null?qr.getRows():pivotResult.rows()));
        result.put("rows", filled.rows());
        if(model.getTimeComparison()!=null){
            var compared=com.wanfo.bi.query.TimeComparison.apply(model,qr.getColumns(),filled.rows(),prior,filled.metadata(),qr.getRows().size()>=(model.getLimit()==null?ChartQueryCompiler.DEFAULT_LIMIT:model.getLimit()));
            result.put("rows",compared.rows());result.put("columns",compared.columns());result.put("timeComparison",compared.metadata());result.put("columnLabels",compared.labels());
        }
        result.put("rowCount", filled.rows().size());
        if(filled.metadata()!=null)result.put("timeFill",filled.metadata());
        result.put("costMs", System.currentTimeMillis() - start);
        if(caps.has("bi:resource:admin")) {result.put("executedSql",sql);result.put("params",params);}
        if(run!=null)result.put("queryDiagnostics",run.diagnostics());
        result.put("datasetName", ds.getName());
        return result;
    }

    /**
     * 数据集可用字段清单（前端配置图表时的字段选择器数据源）。
     */
    private void requireConsumption(Long id){
        if(id==null || id<1)throw new IllegalArgumentException("发布数据集无效");
        caps.require("bi:dashboard:consume");resources.require("dataset",id,"consume");
        var dataset=datasetMapper.selectById(id);
        if(dataset==null || !"table".equals(dataset.getType()))throw new BiAccessException(403,"看板数据集不可用");
        var source=datasourceProvider.getById(dataset.getDatasourceId());
        if(source==null || !"0".equals(source.getStatus()))throw new BiAccessException(403,"看板数据源不可用");
    }
    // Package-private: only published snapshot consumers may use this metadata path.
    List<BiDatasetFieldDO> datasetFieldEntitiesForDashboard(Long id){
        requireConsumption(id);var scope=policies.resolve(id);
        return fieldMapper.selectByDatasetId(id).stream().filter(f->!scope.denied().contains(f.getOriginName())).toList();
    }
    List<Map<String,Object>> datasetFieldsForDashboard(Long id){
        requireConsumption(id);var scope=policies.resolve(id);var result=new ArrayList<Map<String,Object>>();
        for(var f:fieldMapper.selectByDatasetId(id))if(!scope.denied().contains(f.getOriginName())&&!scope.masked().contains(f.getOriginName()))result.add(Map.of("name",f.getOriginName(),"type",String.valueOf(f.getOriginType())));
        return result;
    }
    public List<BiDatasetFieldDO> datasetFieldEntities(Long datasetId)
    {
        requireQuery(datasetId);
        if (datasetMapper.selectById(datasetId) == null) {
            throw new IllegalArgumentException("数据集不存在: " + datasetId);
        }
        var scope = policies.resolve(datasetId);
        return fieldMapper.selectByDatasetId(datasetId).stream()
                .filter(f -> !scope.denied().contains(f.getOriginName()))
                .toList();
    }

    public List<Map<String, Object>> datasetFields(Long datasetId)
    {
        requireQuery(datasetId);
        BiDatasetTableDO ds = datasetMapper.selectById(datasetId);
        if (ds == null)
        {
            throw new IllegalArgumentException("数据集不存在: " + datasetId);
        }
        var scope=policies.resolve(datasetId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (BiDatasetFieldDO f : fieldMapper.selectByDatasetId(datasetId))
        {
            if(scope.denied().contains(f.getOriginName()) || scope.masked().contains(f.getOriginName())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.getOriginName());
            m.put("showName", f.getShowName());
            m.put("type", f.getOriginType());
            m.put("dimFlag", f.getDimFlag());
            m.put("groupName", f.getGroupName());
            m.put("extFlag", f.getExtFlag());
            m.put("extFormula", f.getExtFormula());
            result.add(m);
        }
        return result;
    }

    /** 仅编译不执行（前端"查看 SQL"功能） */
    public String compileOnly(QueryModel model)
    {
        if(model==null) throw new IllegalArgumentException("查询模型不能为空");
        requireQuery(model.getDatasetId());
        BiDatasetTableDO ds = datasetMapper.selectById(model.getDatasetId());
        if (ds == null)
        {
            throw new IllegalArgumentException("数据集不存在: " + model.getDatasetId());
        }
        Map<String,String> valid = new LinkedHashMap<>();
        for (BiDatasetFieldDO f : fieldMapper.selectByDatasetId(model.getDatasetId()))
        {
            valid.put(f.getOriginName(),String.valueOf(f.getOriginType()));
        }
        List<BiDatasetFieldDO> compileFields = fieldMapper.selectByDatasetId(model.getDatasetId());
        String unionSql = "sql".equals(ds.getType())
                ? stripTrailingSemicolon(ds.getSqlText())
                : ds.getUnionSql();
        if (!"sql".equals(ds.getType()))
        { unionSql = com.wanfo.bi.query.DatasetQueryBaseBuilder.expand(unionSql, compileFields); }
        var scope=policies.resolve(model.getDatasetId());
        var restricted=restrictedBase(unionSql, compileFields, scope);
        scope.denied().forEach(valid::remove);scope.masked().forEach(valid::remove);
        return model.getPivot()==null?compiler.compile(model, restricted.sql(), valid).sql:com.wanfo.bi.query.PivotQueryPlan.compile(model,restricted.sql(),valid,compiler,restricted.params()).query().sql;
    }

    private void requireQuery(Long id) {
        if(id==null || id<1) throw new IllegalArgumentException("请选择有效数据集");
        caps.require("bi:dataset:query");resources.require("dataset",id,"view");resources.require("dataset",id,"query");
        var ds=datasetMapper.selectById(id);
        if(ds==null) throw new IllegalArgumentException("数据集不存在");
        if(!"table".equals(ds.getType())) caps.require("bi:resource:admin");
        var source=datasourceProvider.getById(ds.getDatasourceId());
        if(source==null || !"0".equals(source.getStatus())) throw new BiAccessException(403,"数据源不可用");
    }
    private record Restricted(String sql,List<Object> params) { }
    private Restricted restrictedBase(String sql,List<BiDatasetFieldDO> fields,DatasetDataPolicy.Scope scope) {
        Map<String,String> types=new LinkedHashMap<>();
        for(var f:fields) types.put(f.getOriginName(),String.valueOf(f.getOriginType()));
        var plan=DataPolicyCompiler.compile(new ArrayList<>(types.keySet()),types,"\"",scope);
        return new Restricted("SELECT "+plan.projection()+" FROM ("+sql+") AS policy_src"+(plan.predicate().isEmpty()?"":" WHERE "+plan.predicate()),plan.params());
    }

    private String stripTrailingSemicolon(String sql)
    {
        if (sql == null)
        {
            return null;
        }
        String s = sql.trim();
        while (s.endsWith(";"))
        {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }
}

