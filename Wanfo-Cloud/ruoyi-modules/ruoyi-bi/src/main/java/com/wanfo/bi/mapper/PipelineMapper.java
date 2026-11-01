package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PipelineMapper {
    @Select("SELECT id,pipeline_code AS pipelineCode,name,description,status,current_revision_id AS currentRevisionId,create_by AS createBy,create_time AS createTime,update_by AS updateBy,update_time AS updateTime FROM dp_pipeline ORDER BY update_time DESC,id DESC")
    List<Map<String,Object>> list();

    @Select("SELECT id,pipeline_code AS pipelineCode,name,description,status,current_revision_id AS currentRevisionId,create_by AS createBy,create_time AS createTime,update_by AS updateBy,update_time AS updateTime FROM dp_pipeline WHERE id=#{id}")
    Map<String,Object> pipeline(@Param("id") Long id);

    @Insert("INSERT INTO dp_pipeline(pipeline_code,name,description,status,create_by,update_by) VALUES(#{code},#{name},#{description},'DRAFT',#{actor},#{actor})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertPipeline(Map<String,Object> row);

    @Update("UPDATE dp_pipeline SET name=#{name},description=#{description},update_by=#{actor} WHERE id=#{id}")
    int updatePipeline(@Param("id") Long id,@Param("name") String name,@Param("description") String description,@Param("actor") String actor);

    @Select("SELECT id,pipeline_id AS pipelineId,revision_no AS revision,status,lock_version AS lockVersion,rollback_from_revision_id AS rollbackFromRevisionId,definition_hash AS definitionHash,input_schema_json AS inputSchema,output_schema_json AS outputSchema,parameters_json AS parameters,create_by AS createBy,create_time AS createTime,publish_by AS publishBy,publish_time AS publishTime,remark FROM dp_pipeline_revision WHERE pipeline_id=#{pipelineId} ORDER BY revision_no DESC")
    List<Map<String,Object>> revisions(@Param("pipelineId") Long pipelineId);

    @Select("SELECT id,pipeline_id AS pipelineId,revision_no AS revision,status,lock_version AS lockVersion,rollback_from_revision_id AS rollbackFromRevisionId,definition_hash AS definitionHash,input_schema_json AS inputSchema,output_schema_json AS outputSchema,parameters_json AS parameters,create_by AS createBy,create_time AS createTime,publish_by AS publishBy,publish_time AS publishTime,remark FROM dp_pipeline_revision WHERE pipeline_id=#{pipelineId} AND id=#{revisionId}")
    Map<String,Object> revision(@Param("pipelineId") Long pipelineId,@Param("revisionId") Long revisionId);

    @Select("SELECT COALESCE(MAX(revision_no),0)+1 FROM dp_pipeline_revision WHERE pipeline_id=#{pipelineId}")
    int nextRevision(@Param("pipelineId") Long pipelineId);

    @Insert("INSERT INTO dp_pipeline_revision(pipeline_id,revision_no,status,input_schema_json,output_schema_json,parameters_json,create_by,remark) VALUES(#{pipelineId},#{revision},'DRAFT',CAST(#{inputSchema} AS JSON),CAST(#{outputSchema} AS JSON),CAST(#{parameters} AS JSON),#{actor},#{remark})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertRevision(Map<String,Object> row);

    @Update("UPDATE dp_pipeline_revision SET input_schema_json=CAST(#{inputSchema} AS JSON),output_schema_json=CAST(#{outputSchema} AS JSON),parameters_json=CAST(#{parameters} AS JSON),remark=#{remark},lock_version=lock_version+1 WHERE pipeline_id=#{pipelineId} AND id=#{revisionId} AND status='DRAFT' AND lock_version=#{expectedVersion}")
    int updateRevision(@Param("pipelineId") Long pipelineId,@Param("revisionId") Long revisionId,@Param("inputSchema") String inputSchema,@Param("outputSchema") String outputSchema,@Param("parameters") String parameters,@Param("remark") String remark,@Param("expectedVersion") int expectedVersion);

    @Update("UPDATE dp_pipeline_revision SET rollback_from_revision_id=#{sourceRevisionId} WHERE pipeline_id=#{pipelineId} AND id=#{revisionId} AND status='DRAFT' AND lock_version=0")
    int markRollbackDraft(@Param("pipelineId") Long pipelineId,@Param("revisionId") Long revisionId,@Param("sourceRevisionId") Long sourceRevisionId);

    @Insert("INSERT INTO dp_pipeline_publish_history(pipeline_id,previous_revision_id,revision_id,action_type,action_by,reason) VALUES(#{pipelineId},#{previousRevisionId},#{revisionId},#{actionType},#{actor},#{reason})")
    int insertPublishHistory(@Param("pipelineId") Long pipelineId,@Param("previousRevisionId") Long previousRevisionId,@Param("revisionId") Long revisionId,@Param("actionType") String actionType,@Param("actor") String actor,@Param("reason") String reason);

    @Select("SELECT id,pipeline_id AS pipelineId,previous_revision_id AS previousRevisionId,revision_id AS revisionId,action_type AS actionType,action_by AS actionBy,reason,create_time AS createTime FROM dp_pipeline_publish_history WHERE pipeline_id=#{pipelineId} ORDER BY id DESC LIMIT #{limit}")
    List<Map<String,Object>> publishHistory(@Param("pipelineId") Long pipelineId,@Param("limit") int limit);

    @Select("SELECT id,revision_id AS revisionId,node_key AS nodeKey,node_type AS nodeType,name,config_json AS config,input_schema_json AS inputSchema,output_schema_json AS outputSchema,position_x AS positionX,position_y AS positionY,order_num AS orderNum FROM dp_pipeline_node WHERE revision_id=#{revisionId} ORDER BY order_num,id")
    List<Map<String,Object>> nodes(@Param("revisionId") Long revisionId);

    @Select("SELECT id,revision_id AS revisionId,source_node_key AS sourceNodeKey,source_port AS sourcePort,target_node_key AS targetNodeKey,target_port AS targetPort,edge_type AS edgeType,order_num AS orderNum FROM dp_pipeline_edge WHERE revision_id=#{revisionId} ORDER BY order_num,id")
    List<Map<String,Object>> edges(@Param("revisionId") Long revisionId);

    @Delete("DELETE FROM dp_pipeline_node WHERE revision_id=#{revisionId}") int deleteNodes(@Param("revisionId") Long revisionId);
    @Delete("DELETE FROM dp_pipeline_edge WHERE revision_id=#{revisionId}") int deleteEdges(@Param("revisionId") Long revisionId);

    @Insert("INSERT INTO dp_pipeline_node(revision_id,node_key,node_type,name,config_json,input_schema_json,output_schema_json,position_x,position_y,order_num) VALUES(#{revisionId},#{nodeKey},#{nodeType},#{name},CAST(#{config} AS JSON),CAST(#{inputSchema} AS JSON),CAST(#{outputSchema} AS JSON),#{positionX},#{positionY},#{orderNum})")
    int insertNode(@Param("revisionId") Long revisionId,@Param("nodeKey") String nodeKey,@Param("nodeType") String nodeType,@Param("name") String name,@Param("config") String config,@Param("inputSchema") String inputSchema,@Param("outputSchema") String outputSchema,@Param("positionX") int positionX,@Param("positionY") int positionY,@Param("orderNum") int orderNum);

    @Insert("INSERT INTO dp_pipeline_edge(revision_id,source_node_key,source_port,target_node_key,target_port,edge_type,order_num) VALUES(#{revisionId},#{source},#{sourcePort},#{target},#{targetPort},#{type},#{orderNum})")
    int insertEdge(@Param("revisionId") Long revisionId,@Param("source") String source,@Param("sourcePort") String sourcePort,@Param("target") String target,@Param("targetPort") String targetPort,@Param("type") String type,@Param("orderNum") int orderNum);

    @Update("UPDATE dp_pipeline_revision SET status='PUBLISHED',definition_hash=#{hash},publish_by=#{actor},publish_time=CURRENT_TIMESTAMP(3) WHERE pipeline_id=#{pipelineId} AND id=#{revisionId} AND status='DRAFT' AND lock_version=#{expectedVersion}")
    int publishRevision(@Param("pipelineId") Long pipelineId,@Param("revisionId") Long revisionId,@Param("hash") String hash,@Param("actor") String actor,@Param("expectedVersion") int expectedVersion);

    @Update("UPDATE dp_pipeline SET status='PUBLISHED',current_revision_id=#{revisionId},update_by=#{actor} WHERE id=#{pipelineId} AND current_revision_id <=> #{previousRevisionId,jdbcType=BIGINT}")
    int markPublished(@Param("pipelineId") Long pipelineId,@Param("revisionId") Long revisionId,@Param("previousRevisionId") Long previousRevisionId,@Param("actor") String actor);
}
