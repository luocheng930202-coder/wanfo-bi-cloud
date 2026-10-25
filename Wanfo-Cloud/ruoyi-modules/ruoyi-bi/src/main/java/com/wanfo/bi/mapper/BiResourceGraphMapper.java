package com.wanfo.bi.mapper;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface BiResourceGraphMapper {
    record Node(String type,Long id,String name,String publishedName,boolean published){}
    record Edge(String sourceType,Long sourceId,String targetType,Long targetId,String kind){}
    @Select("SELECT * FROM (SELECT 'datasource' AS type,id,name,NULL AS publishedName,0 AS published FROM bi_datasource WHERE del_flag='0' UNION ALL SELECT 'dataset',id,name,NULL,0 FROM bi_dataset_table WHERE del_flag='0' UNION ALL SELECT 'chart',id,name,NULL,0 FROM bi_chart_view WHERE del_flag='0' AND definition_json IS NOT NULL UNION ALL SELECT 'dashboard',id,name,JSON_UNQUOTE(JSON_EXTRACT(published_json,'$.name')),published_json IS NOT NULL FROM bi_dashboard WHERE del_flag='0' AND definition_json IS NOT NULL) n ORDER BY type,id LIMIT 2001")
    List<Node> nodes();
    @Select("SELECT * FROM (SELECT 'datasource' AS sourceType,datasource_id AS sourceId,'dataset' AS targetType,id AS targetId,'binding' AS kind FROM bi_dataset_table WHERE del_flag='0' AND datasource_id IS NOT NULL UNION ALL SELECT 'dataset',dataset_id,'chart',id,'binding' FROM bi_chart_view WHERE del_flag='0' AND definition_json IS NOT NULL UNION ALL SELECT 'chart',b.chart_id,'dashboard',b.dashboard_id,b.binding_kind FROM bi_dashboard_binding b JOIN bi_dashboard d ON d.id=b.dashboard_id AND d.del_flag='0') e LIMIT 6001")
    List<Edge> edges();
}
