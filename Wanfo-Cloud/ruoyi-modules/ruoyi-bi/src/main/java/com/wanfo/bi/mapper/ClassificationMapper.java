package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ClassificationMapper {

    @Select("""
            SELECT code,name,rank_no AS rankNo,default_enforcement AS defaultEnforcement,description
            FROM dp_sensitivity_level ORDER BY rank_no
            """)
    List<Map<String, Object>> listLevels();

    @Select("""
            SELECT code,name,rank_no AS rankNo,default_enforcement AS defaultEnforcement,description
            FROM dp_sensitivity_level WHERE code=#{code}
            """)
    Map<String, Object> level(@Param("code") String code);

    @Select("""
            SELECT a.id AS assetId,a.asset_type AS assetType,a.name,a.qualified_name AS qualifiedName,
                   a.classification AS assetClassification,
                   c.level_code AS levelCode,c.source,c.inherited_from_asset_id AS inheritedFromAssetId,
                   c.note,c.update_by AS updateBy,c.update_time AS updateTime,
                   l.rank_no AS rankNo,l.default_enforcement AS defaultEnforcement
            FROM dp_asset a
            LEFT JOIN dp_asset_classification c ON c.asset_id=a.id
            LEFT JOIN dp_sensitivity_level l ON l.code=COALESCE(c.level_code,a.classification)
            WHERE a.id=#{assetId}
            """)
    Map<String, Object> effective(@Param("assetId") Long assetId);

    @Insert("""
            INSERT INTO dp_asset_classification(asset_id,level_code,source,inherited_from_asset_id,note,update_by)
            VALUES(#{assetId},#{levelCode},#{source},#{inheritedFromAssetId},#{note},#{updateBy})
            ON DUPLICATE KEY UPDATE level_code=VALUES(level_code),source=VALUES(source),
                inherited_from_asset_id=VALUES(inherited_from_asset_id),note=VALUES(note),update_by=VALUES(update_by)
            """)
    int upsert(Map<String, Object> row);

    @Update("UPDATE dp_asset SET classification=#{levelCode},update_by=#{updateBy} WHERE id=#{assetId}")
    int mirrorAsset(@Param("assetId") Long assetId, @Param("levelCode") String levelCode, @Param("updateBy") String updateBy);

    @Delete("DELETE FROM dp_asset_classification WHERE asset_id=#{assetId} AND source='INHERITED'")
    int clearInherited(@Param("assetId") Long assetId);

    @Select("""
            SELECT e.id AS edgeId,e.source_asset_id AS sourceAssetId,e.target_asset_id AS targetAssetId
            FROM dp_lineage_edge e
            JOIN dp_asset s ON s.id=e.source_asset_id
            JOIN dp_asset t ON t.id=e.target_asset_id
            WHERE e.status='ACTIVE' AND e.lineage_level='COLUMN'
            ORDER BY e.id LIMIT 20000
            """)
    List<Map<String, Object>> activeColumnEdges();

    @Select("""
            SELECT c.name AS fieldName,
                   COALESCE(cls.level_code,c.classification,'PUBLIC') AS levelCode,
                   COALESCE(lvl.default_enforcement,'NONE') AS enforcement,
                   COALESCE(lvl.rank_no,10) AS rankNo
            FROM dp_asset t
            JOIN dp_asset c ON c.parent_id=t.id AND c.asset_type='COLUMN' AND c.status IN ('ACTIVE','STALE')
            LEFT JOIN dp_asset_classification cls ON cls.asset_id=c.id
            LEFT JOIN dp_sensitivity_level lvl ON lvl.code=COALESCE(cls.level_code,c.classification,'PUBLIC')
            WHERE t.source_kind='CONNECTOR' AND t.source_id=#{datasourceId}
              AND t.asset_type IN ('TABLE','VIEW') AND t.name=#{tableName}
              AND t.status IN ('ACTIVE','STALE')
            """)
    List<Map<String, Object>> connectorColumnLevels(@Param("datasourceId") Long datasourceId,
            @Param("tableName") String tableName);

    @Select("SELECT id,asset_type AS assetType,status FROM dp_asset WHERE id=#{id}")
    Map<String, Object> asset(@Param("id") Long id);
}
