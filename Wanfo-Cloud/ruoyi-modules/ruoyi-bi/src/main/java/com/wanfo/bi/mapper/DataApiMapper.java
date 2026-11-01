package com.wanfo.bi.mapper;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DataApiMapper
{
    String COLS="id,api_code AS apiCode,family_code AS familyCode,name,metric_id AS metricId,metric_published_version AS metricPublishedVersion,"
            + "api_version AS apiVersion,status,deprecated_at AS deprecatedAt,sunset_at AS sunsetAt,"
            + "successor_api_code AS successorApiCode,deprecation_note AS deprecationNote,"
            + "contract_schema_json AS contractSchemaJson,contract_checked_at AS contractCheckedAt,"
            + "canary_percent AS canaryPercent,"
            + "contract_sample_json AS contractSampleJson,contract_sample_at AS contractSampleAt,"
            + "max_page_size AS maxPageSize,timeout_seconds AS timeoutSeconds,calls_per_minute AS callsPerMinute,"
            + "daily_quota AS dailyQuota,owner_actor_json AS ownerActorJson,create_by AS createBy,create_time AS createTime,"
            + "update_by AS updateBy,update_time AS updateTime";
    @Select("SELECT "+COLS+" FROM dp_data_api ORDER BY id DESC") List<Map<String,Object>> list();
    @Select("SELECT "+COLS+" FROM dp_data_api WHERE id=#{id}") Map<String,Object> get(@Param("id") Long id);
    @Select("SELECT "+COLS+" FROM dp_data_api WHERE api_code=#{code}") Map<String,Object> byCode(@Param("code") String code);
    @Select("SELECT "+COLS+" FROM dp_data_api WHERE family_code=#{family} ORDER BY api_version DESC,id DESC")
    List<Map<String, Object>> listByFamily(@Param("family") String family);
    @Select("SELECT COALESCE(MAX(api_version),0) FROM dp_data_api WHERE family_code=#{family}")
    int maxApiVersionInFamily(@Param("family") String family);
    @Insert("INSERT INTO dp_data_api(api_code,family_code,name,metric_id,status,api_version,max_page_size,timeout_seconds,calls_per_minute,daily_quota,owner_actor_json,create_by,update_by) VALUES(#{code},#{familyCode},#{name},#{metricId},'DRAFT',#{apiVersion},#{pageSize},#{timeout},#{rate},#{quota},#{actor},#{user},#{user})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(Map<String,Object> row);
    @Update("UPDATE dp_data_api SET name=#{name},metric_id=#{metricId},family_code=#{familyCode},api_version=#{apiVersion},max_page_size=#{pageSize},timeout_seconds=#{timeout},calls_per_minute=#{rate},daily_quota=#{quota},update_by=#{user},update_time=NOW(3) WHERE id=#{id} AND status='DRAFT'")
    int update(Map<String,Object> row);
    @Update("UPDATE dp_data_api SET status='ACTIVE',metric_published_version=#{metricVersion},contract_schema_json=#{contractSchema},contract_checked_at=NOW(3),contract_sample_json=#{contractSample},contract_sample_at=NOW(3),update_by=#{user},update_time=NOW(3) WHERE id=#{id} AND status='DRAFT'")
    int publish(@Param("id") Long id, @Param("metricVersion") int metricVersion,
            @Param("contractSchema") String contractSchema, @Param("contractSample") String contractSample,
            @Param("user") String user);

    @Update("""
            UPDATE dp_data_api SET contract_sample_json=#{contractSample},contract_sample_at=NOW(3),
                update_by=#{user},update_time=NOW(3)
            WHERE id=#{id} AND status IN ('ACTIVE','DEPRECATED')
            """)
    int saveContractSample(@Param("id") Long id, @Param("contractSample") String contractSample,
            @Param("user") String user);

    @Select("SELECT "+COLS+" FROM dp_data_api WHERE successor_api_code=#{code} AND status IN ('ACTIVE','DEPRECATED') AND contract_schema_json IS NOT NULL AND contract_schema_json<>'' ORDER BY id DESC LIMIT 1")
    Map<String, Object> baselineBySuccessorCode(@Param("code") String code);
    @Update("""
            UPDATE dp_data_api SET status='DEPRECATED',deprecated_at=NOW(3),sunset_at=#{sunsetAt},
                successor_api_code=#{successorApiCode},deprecation_note=#{deprecationNote},
                update_by=#{user},update_time=NOW(3)
            WHERE id=#{id} AND status='ACTIVE'
            """)
    int deprecate(@Param("id") Long id, @Param("sunsetAt") LocalDateTime sunsetAt,
            @Param("successorApiCode") String successorApiCode, @Param("deprecationNote") String deprecationNote,
            @Param("user") String user);
    @Update("""
            UPDATE dp_data_api SET status='ACTIVE',deprecated_at=NULL,sunset_at=NULL,successor_api_code=NULL,
                deprecation_note=NULL,update_by=#{user},update_time=NOW(3)
            WHERE id=#{id} AND status='DEPRECATED'
            """)
    int clearDeprecation(@Param("id") Long id, @Param("user") String user);
    @Update("UPDATE dp_data_api SET status='DISABLED',update_by=#{user},update_time=NOW(3) WHERE id=#{id} AND status IN ('ACTIVE','DEPRECATED')") int disable(@Param("id") Long id,@Param("user") String user);
    @Update("""
            UPDATE dp_data_api SET canary_percent=#{percent},update_by=#{user},update_time=NOW(3)
            WHERE id=#{id} AND status='ACTIVE'
            """)
    int setCanaryPercent(@Param("id") Long id, @Param("percent") int percent, @Param("user") String user);
    @Update("""
            UPDATE dp_data_api SET canary_percent=0,update_by=#{user},update_time=NOW(3)
            WHERE family_code=#{family} AND id<>#{exceptId} AND canary_percent>0
            """)
    int clearOtherCanaries(@Param("family") String family, @Param("exceptId") Long exceptId, @Param("user") String user);
    @Select("SELECT COALESCE(MAX(revision_no),0)+1 FROM dp_data_api_revision WHERE api_id=#{id}") int nextRevision(@Param("id") Long id);
    @Insert("INSERT INTO dp_data_api_revision(api_id,revision_no,definition_json,created_by) VALUES(#{id},#{revision},#{definition},#{user})") int insertRevision(@Param("id") Long id,@Param("revision") int revision,@Param("definition") String definition,@Param("user") String user);
    @Select("SELECT id,revision_no AS revisionNo,definition_json AS definitionJson,created_by AS createdBy,created_at AS createdAt FROM dp_data_api_revision WHERE api_id=#{id} ORDER BY revision_no DESC") List<Map<String,Object>> revisions(@Param("id") Long id);
    @Select("""
            SELECT k.id,k.api_id AS apiId,k.key_prefix AS keyPrefix,k.caller_id AS callerId,
                   ca.org_code AS callerOrgCode,ca.org_name AS callerOrgName,
                   k.status,k.create_by AS createBy,k.created_at AS createdAt,k.expires_at AS expiresAt,
                   k.last_used_at AS lastUsedAt,k.revoked_at AS revokedAt
            FROM dp_data_api_key k
            LEFT JOIN dp_data_api_caller ca ON ca.id=k.caller_id
            WHERE k.api_id=#{id} ORDER BY k.id DESC
            """)
    List<Map<String, Object>> keys(@Param("id") Long id);
    @Select("SELECT id,api_id AS apiId,caller_id AS callerId FROM dp_data_api_key WHERE key_hash=#{hash} AND status='ACTIVE' AND (expires_at IS NULL OR expires_at>NOW(3))") Map<String,Object> authenticate(@Param("hash") String hash);
    @Insert("INSERT INTO dp_data_api_key(api_id,key_hash,key_prefix,caller_id,create_by,expires_at) VALUES(#{apiId},#{hash},#{prefix},#{callerId},#{user},#{expiresAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertKey(Map<String, Object> row);

    @Update("UPDATE dp_data_api_key SET caller_id=#{callerId} WHERE id=#{keyId} AND api_id=#{apiId}")
    int assignKeyCaller(@Param("apiId") Long apiId, @Param("keyId") Long keyId, @Param("callerId") Long callerId);
    @Update("UPDATE dp_data_api_key SET status='REVOKED',revoked_at=NOW(3) WHERE api_id=#{apiId} AND status='ACTIVE'") int revokeActiveKeys(@Param("apiId") Long apiId);
    @Update("UPDATE dp_data_api_key SET status='REVOKED',revoked_at=NOW(3) WHERE id=#{keyId} AND api_id=#{apiId} AND status='ACTIVE'") int revokeKey(@Param("apiId") Long apiId,@Param("keyId") Long keyId);
    @Update("UPDATE dp_data_api_key SET last_used_at=NOW(3) WHERE id=#{id} AND status='ACTIVE'") int touchKey(@Param("id") Long id);
    @Insert("INSERT INTO dp_data_api_usage_counter(api_id,api_key_id,window_kind,window_start,call_count) VALUES(#{apiId},#{keyId},#{kind},#{window},1) ON DUPLICATE KEY UPDATE call_count=LEAST(call_count+1,#{limit}+1)") int incrementWindow(@Param("apiId") Long apiId,@Param("keyId") Long keyId,@Param("kind") String kind,@Param("window") LocalDateTime window,@Param("limit") int limit);
    @Select("SELECT call_count AS callCount FROM dp_data_api_usage_counter WHERE api_id=#{apiId} AND api_key_id=#{keyId} AND window_kind=#{kind} AND window_start=#{window}") Integer windowCount(@Param("apiId") Long apiId,@Param("keyId") Long keyId,@Param("kind") String kind,@Param("window") LocalDateTime window);
    @Insert("""
            INSERT INTO dp_data_api_call(api_id,api_key_id,status,duration_ms,row_count,error_code,sample_json)
            VALUES(#{apiId},#{keyId},#{status},#{duration},#{rows},#{error},#{sampleJson})
            """)
    int call(@Param("apiId") Long apiId,@Param("keyId") Long keyId,@Param("status") String status,@Param("duration") int duration,@Param("rows") int rows,@Param("error") String error,@Param("sampleJson") String sampleJson);
    @Select("SELECT id,api_key_id AS apiKeyId,status,duration_ms AS durationMs,row_count AS rowCount,error_code AS errorCode,called_at AS calledAt FROM dp_data_api_call WHERE api_id=#{id} ORDER BY id DESC LIMIT 200") List<Map<String,Object>> calls(@Param("id") Long id);

    @Select("""
            SELECT c.api_id AS apiId,
                   COUNT(*) AS callCount30d,
                   SUM(CASE WHEN c.status='SUCCESS' THEN 1 ELSE 0 END) AS successCount30d,
                   COUNT(DISTINCT c.api_key_id) AS distinctKeys30d,
                   MAX(c.called_at) AS lastCalledAt
            FROM dp_data_api_call c
            WHERE c.called_at >= DATE_SUB(NOW(3), INTERVAL 30 DAY)
            GROUP BY c.api_id
            """)
    List<Map<String, Object>> callStats30d();

    @Select("""
            SELECT c.api_id AS apiId, k.key_prefix AS keyPrefix, COUNT(*) AS callCount30d, MAX(c.called_at) AS lastCalledAt
            FROM dp_data_api_call c
            INNER JOIN dp_data_api_key k ON k.id=c.api_key_id
            WHERE c.called_at >= DATE_SUB(NOW(3), INTERVAL 30 DAY)
            GROUP BY c.api_id, k.key_prefix
            ORDER BY c.api_id, callCount30d DESC
            """)
    List<Map<String, Object>> callerKeyPrefixes30d();
}
