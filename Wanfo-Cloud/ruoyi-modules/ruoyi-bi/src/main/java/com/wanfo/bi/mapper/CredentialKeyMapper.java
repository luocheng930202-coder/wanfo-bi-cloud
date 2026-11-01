package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CredentialKeyMapper {

    @Insert("""
            INSERT INTO dp_credential_key_meta(version_no,key_alias,provider,fingerprint,status,note,update_by)
            VALUES(#{version},#{alias},#{provider},#{fingerprint},#{status},#{note},#{actor})
            ON DUPLICATE KEY UPDATE key_alias=VALUES(key_alias),provider=VALUES(provider),
                fingerprint=VALUES(fingerprint),status=VALUES(status),note=VALUES(note),update_by=VALUES(update_by)
            """)
    int upsertMeta(Map<String, Object> row);

    @Update("UPDATE dp_credential_key_meta SET status='AVAILABLE' WHERE status='ACTIVE'")
    int clearActive();

    @Select("""
            SELECT version_no AS version,key_alias AS alias,provider,fingerprint,status,note,
                   update_by AS updateBy,update_time AS updateTime
            FROM dp_credential_key_meta ORDER BY version_no
            """)
    List<Map<String, Object>> listMeta();

    @Insert("""
            INSERT INTO dp_credential_key_rotation(from_versions_json,to_version,scanned,rewritten,skipped,failed,reason,actor)
            VALUES(CAST(#{fromVersionsJson} AS JSON),#{toVersion},#{scanned},#{rewritten},#{skipped},#{failed},#{reason},#{actor})
            """)
    int insertRotation(Map<String, Object> row);

    @Select("""
            SELECT id,CAST(from_versions_json AS CHAR) AS fromVersionsJson,to_version AS toVersion,
                   scanned,rewritten,skipped,failed,reason,actor,create_time AS createTime
            FROM dp_credential_key_rotation ORDER BY id DESC LIMIT 50
            """)
    List<Map<String, Object>> recentRotations();
}
