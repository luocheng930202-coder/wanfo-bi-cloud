package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BackupDrillMapper {

    @Select("""
            SELECT id,drill_type AS drillType,CAST(scope_json AS CHAR) AS scopeJson,result,
                   rpo_minutes AS rpoMinutes,rto_minutes AS rtoMinutes,artifact_path AS artifactPath,
                   CAST(checklist_json AS CHAR) AS checklistJson,notes,actor,
                   started_at AS startedAt,finished_at AS finishedAt,create_time AS createTime
            FROM dp_backup_drill ORDER BY id DESC LIMIT 100
            """)
    List<Map<String, Object>> list();

    @Select("""
            SELECT id,drill_type AS drillType,CAST(scope_json AS CHAR) AS scopeJson,result,
                   rpo_minutes AS rpoMinutes,rto_minutes AS rtoMinutes,artifact_path AS artifactPath,
                   CAST(checklist_json AS CHAR) AS checklistJson,notes,actor,
                   started_at AS startedAt,finished_at AS finishedAt,create_time AS createTime
            FROM dp_backup_drill WHERE id=#{id}
            """)
    Map<String, Object> find(@Param("id") Long id);

    @Insert("""
            INSERT INTO dp_backup_drill(drill_type,scope_json,result,rpo_minutes,rto_minutes,artifact_path,
                checklist_json,notes,actor,started_at,finished_at)
            VALUES(#{drillType},CAST(#{scopeJson} AS JSON),#{result},#{rpoMinutes},#{rtoMinutes},#{artifactPath},
                CAST(#{checklistJson} AS JSON),#{notes},#{actor},#{startedAt},#{finishedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Select("SELECT version FROM bi_flyway_history ORDER BY installed_rank DESC LIMIT 1")
    String latestFlywayVersion();

    @Select("SELECT live_retention_days AS liveRetentionDays,updated_by AS updatedBy,updated_at AS updatedAt FROM dp_audit_retention_policy WHERE policy_key='global'")
    Map<String, Object> auditRetention();

    @Select("SELECT COUNT(*) FROM dp_audit_archive")
    Long archiveCount();

    @Select("SELECT artifact_name AS artifactName FROM dp_backup_artifact_watch WHERE artifact_name=#{name}")
    String watchedArtifact(@Param("name") String name);

    @Insert("""
            INSERT INTO dp_backup_artifact_watch(artifact_name,artifact_path,size_bytes,drill_id)
            VALUES(#{artifactName},#{artifactPath},#{sizeBytes},#{drillId})
            """)
    int watchArtifact(Map<String, Object> row);
}
