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
public interface FieldStandardMapper {

    @Select("""
            SELECT id,standard_code AS standardCode,domain_code AS domainCode,name,data_type_family AS dataTypeFamily,max_length AS maxLength,
                   nullable_policy AS nullablePolicy,value_pattern AS valuePattern,
                   CAST(enum_values_json AS CHAR) AS enumValuesJson,
                   CAST(match_aliases_json AS CHAR) AS matchAliasesJson,
                   glossary_term_id AS glossaryTermId,metric_id AS metricId,description,status,
                   enforce_gate AS enforceGate,
                   create_by AS createBy,create_time AS createTime,update_by AS updateBy,update_time AS updateTime
            FROM dp_field_standard ORDER BY id DESC LIMIT 500
            """)
    List<Map<String, Object>> list();

    @Select("""
            SELECT id,standard_code AS standardCode,domain_code AS domainCode,name,data_type_family AS dataTypeFamily,max_length AS maxLength,
                   nullable_policy AS nullablePolicy,value_pattern AS valuePattern,
                   CAST(enum_values_json AS CHAR) AS enumValuesJson,
                   CAST(match_aliases_json AS CHAR) AS matchAliasesJson,
                   glossary_term_id AS glossaryTermId,metric_id AS metricId,description,status,
                   enforce_gate AS enforceGate,
                   create_by AS createBy,create_time AS createTime,update_by AS updateBy,update_time AS updateTime
            FROM dp_field_standard WHERE id=#{id}
            """)
    Map<String, Object> find(@Param("id") Long id);

    @Select("SELECT id FROM dp_glossary_term WHERE id=#{id}")
    Long glossaryExists(@Param("id") Long id);

    @Select("SELECT id FROM bi_metric WHERE id=#{id}")
    Long metricExists(@Param("id") Long id);

    @Insert("""
            INSERT INTO dp_field_standard(standard_code,domain_code,name,data_type_family,max_length,nullable_policy,value_pattern,
                enum_values_json,match_aliases_json,glossary_term_id,metric_id,description,status,enforce_gate,create_by)
            VALUES(#{standardCode},#{domainCode},#{name},#{dataTypeFamily},#{maxLength},#{nullablePolicy},#{valuePattern},
                CAST(#{enumValuesJson} AS JSON),CAST(#{matchAliasesJson} AS JSON),#{glossaryTermId},#{metricId},
                #{description},#{status},#{enforceGate},#{createBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Update("""
            UPDATE dp_field_standard SET domain_code=#{domainCode},name=#{name},data_type_family=#{dataTypeFamily},max_length=#{maxLength},
                nullable_policy=#{nullablePolicy},value_pattern=#{valuePattern},
                enum_values_json=CAST(#{enumValuesJson} AS JSON),match_aliases_json=CAST(#{matchAliasesJson} AS JSON),
                glossary_term_id=#{glossaryTermId},metric_id=#{metricId},description=#{description},
                status=#{status},enforce_gate=#{enforceGate},update_by=#{updateBy}
            WHERE id=#{id}
            """)
    int update(Map<String, Object> row);

    @Select("""
            SELECT b.id,b.standard_id AS standardId,s.standard_code AS standardCode,s.name AS standardName,
                   b.asset_id AS assetId,a.name AS columnName,a.qualified_name AS qualifiedName,
                   b.bind_source AS bindSource,b.note,b.create_by AS createBy,b.create_time AS createTime
            FROM dp_field_standard_binding b
            JOIN dp_field_standard s ON s.id=b.standard_id
            JOIN dp_asset a ON a.id=b.asset_id
            ORDER BY b.id DESC LIMIT 1000
            """)
    List<Map<String, Object>> listBindings();

    @Select("SELECT id,standard_id AS standardId,asset_id AS assetId,bind_source AS bindSource FROM dp_field_standard_binding WHERE asset_id=#{assetId}")
    Map<String, Object> bindingByAsset(@Param("assetId") Long assetId);

    @Insert("""
            INSERT INTO dp_field_standard_binding(standard_id,asset_id,bind_source,note,create_by)
            VALUES(#{standardId},#{assetId},#{bindSource},#{note},#{createBy})
            ON DUPLICATE KEY UPDATE standard_id=VALUES(standard_id),bind_source=VALUES(bind_source),
                note=VALUES(note),create_by=VALUES(create_by)
            """)
    int upsertBinding(Map<String, Object> row);

    @Delete("DELETE FROM dp_field_standard_binding WHERE asset_id=#{assetId}")
    int deleteBinding(@Param("assetId") Long assetId);

    @Select("""
            SELECT c.id AS assetId,c.name AS columnName,c.qualified_name AS qualifiedName,
                   t.name AS tableName,t.id AS tableAssetId,
                   CAST(v.metadata_json AS CHAR) AS metadataJson
            FROM dp_asset c
            LEFT JOIN dp_asset t ON t.id=c.parent_id
            LEFT JOIN dp_asset_version v ON v.id=c.current_version_id
            WHERE c.asset_type='COLUMN' AND c.status IN ('ACTIVE','STALE')
            ORDER BY c.id DESC LIMIT #{limit}
            """)
    List<Map<String, Object>> recentColumns(@Param("limit") int limit);

    @Select("""
            SELECT c.id AS assetId,c.name AS columnName,c.qualified_name AS qualifiedName,
                   t.name AS tableName,t.id AS tableAssetId,
                   CAST(v.metadata_json AS CHAR) AS metadataJson
            FROM dp_asset c
            LEFT JOIN dp_asset t ON t.id=c.parent_id
            LEFT JOIN dp_asset_version v ON v.id=c.current_version_id
            WHERE c.asset_type='COLUMN' AND c.status IN ('ACTIVE','STALE')
              AND (c.parent_id=#{tableAssetId} OR c.id=#{tableAssetId})
            ORDER BY c.id
            """)
    List<Map<String, Object>> columnsUnder(@Param("tableAssetId") Long tableAssetId);

    @Select("SELECT id,asset_type AS assetType,name,status FROM dp_asset WHERE id=#{id}")
    Map<String, Object> asset(@Param("id") Long id);

    /** COLUMN → TABLE parent + datasource for JDBC online sample. */
    @Select("""
            SELECT c.id AS assetId,c.name AS columnName,c.asset_type AS assetType,c.source_id AS sourceId,
                   c.qualified_name AS qualifiedName,t.id AS tableAssetId,t.name AS tableName,
                   t.asset_type AS parentAssetType
            FROM dp_asset c
            LEFT JOIN dp_asset t ON t.id=c.parent_id
            WHERE c.id=#{assetId}
            """)
    Map<String, Object> columnSampleContext(@Param("assetId") Long assetId);
}
