package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WarehouseTableModelMapper {

    @Select("""
            SELECT id,model_code AS modelCode,name,layer,table_name AS tableName,table_kind AS tableKind,
                   primary_keys_json AS primaryKeysJson,partition_field AS partitionField,
                   update_strategy AS updateStrategy,consumer_gate AS consumerGate,
                   freshness_sla_minutes AS freshnessSlaMinutes,description,status,create_by AS createBy,
                   create_time AS createTime,update_by AS updateBy,update_time AS updateTime
            FROM dp_warehouse_table_model ORDER BY id DESC LIMIT 500
            """)
    List<Map<String, Object>> list();

    @Select("""
            SELECT id,model_code AS modelCode,name,layer,table_name AS tableName,table_kind AS tableKind,
                   primary_keys_json AS primaryKeysJson,partition_field AS partitionField,
                   update_strategy AS updateStrategy,consumer_gate AS consumerGate,
                   freshness_sla_minutes AS freshnessSlaMinutes,description,status,create_by AS createBy,
                   create_time AS createTime,update_by AS updateBy,update_time AS updateTime
            FROM dp_warehouse_table_model WHERE id=#{id}
            """)
    Map<String, Object> find(@Param("id") Long id);

    @Select("SELECT id,model_code AS modelCode,table_name AS tableName,status,consumer_gate AS consumerGate,freshness_sla_minutes AS freshnessSlaMinutes FROM dp_warehouse_table_model WHERE id=#{id}")
    Map<String, Object> consumerBinding(@Param("id") Long id);

    @Select("""
            SELECT id,model_code AS modelCode,name,layer,table_name AS tableName,table_kind AS tableKind,
                   primary_keys_json AS primaryKeysJson,partition_field AS partitionField,
                   update_strategy AS updateStrategy,consumer_gate AS consumerGate,
                   freshness_sla_minutes AS freshnessSlaMinutes,description,status
            FROM dp_warehouse_table_model WHERE model_code=#{code}
            """)
    Map<String, Object> findByCode(@Param("code") String code);

    @Insert("""
            INSERT INTO dp_warehouse_table_model(model_code,name,layer,table_name,table_kind,primary_keys_json,
                partition_field,update_strategy,consumer_gate,freshness_sla_minutes,description,status,create_by)
            VALUES(#{modelCode},#{name},#{layer},#{tableName},#{tableKind},#{primaryKeysJson},
                #{partitionField},#{updateStrategy},#{consumerGate},#{freshnessSlaMinutes},#{description},#{status},#{createBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Update("""
            UPDATE dp_warehouse_table_model SET name=#{name},primary_keys_json=#{primaryKeysJson},
                partition_field=#{partitionField},update_strategy=#{updateStrategy},
                consumer_gate=#{consumerGate},freshness_sla_minutes=#{freshnessSlaMinutes},
                description=#{description},status=#{status},update_by=#{updateBy}
            WHERE id=#{id}
            """)
    int update(Map<String, Object> row);
}
