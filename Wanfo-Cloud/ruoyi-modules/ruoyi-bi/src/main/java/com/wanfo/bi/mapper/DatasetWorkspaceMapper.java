package com.wanfo.bi.mapper;

import java.util.*;
import com.wanfo.bi.domain.*;
import org.apache.ibatis.annotations.*;

/** Workspace writes are explicit, including clearing nullable SQL/model properties. */
public interface DatasetWorkspaceMapper {
    @Select("SELECT id FROM bi_dataset_table WHERE id=#{id} AND del_flag='0' FOR UPDATE")
    Long lock(Long id);

    @Update("UPDATE bi_dataset_table SET name=#{name},group_id=#{groupId},type='table',datasource_id=#{datasourceId},source_catalog=#{sourceCatalog},source_table=#{sourceTable},sql_text=NULL,union_sql=#{unionSql},model_json=#{modelJson},warehouse_model_id=#{warehouseModelId},remark=#{remark},update_by=#{updateBy},update_time=NOW() WHERE id=#{id} AND del_flag='0'")
    int updateDefinition(BiDatasetTableDO row);

    @Update("UPDATE bi_dataset_field SET origin_type=#{originType},show_name=#{showName},dim_flag=#{dimFlag},ext_flag=#{extFlag},ext_formula=#{extFormula},group_name=#{groupName},order_num=#{orderNum},update_time=NOW() WHERE id=#{id} AND dataset_id=#{datasetId} AND del_flag='0'")
    int updateFieldDefinition(BiDatasetFieldDO row);

    @Select("SELECT id,dataset_id AS datasetId,name,type,definition_json AS definitionJson,version FROM bi_chart_view WHERE dataset_id=#{id} AND del_flag='0' ORDER BY id")
    List<BiChartDO> charts(Long id);

    @Select("SELECT (SELECT COUNT(*) FROM bi_metric WHERE dataset_id=#{id} AND del_flag='0') AS metrics,(SELECT COUNT(*) FROM dp_dataset_scope_binding WHERE dataset_id=#{id}) AS warehouseScopes")
    Map<String,Object> additionalDependencies(Long id);

    @Select("SELECT DISTINCT d.id,d.name,d.version,d.published_version AS publishedVersion FROM bi_dashboard_binding b JOIN bi_dashboard d ON d.id=b.dashboard_id WHERE b.dataset_id=#{id} AND d.del_flag='0' ORDER BY d.id")
    List<Map<String,Object>> dashboards(Long id);
}
