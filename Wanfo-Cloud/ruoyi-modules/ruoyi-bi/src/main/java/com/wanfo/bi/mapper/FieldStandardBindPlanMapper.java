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
public interface FieldStandardBindPlanMapper {

    @Select("""
            SELECT id,name,status,CAST(bindings_json AS CHAR) AS bindingsJson,create_by AS createBy,create_time AS createTime,
                   approved_by AS approvedBy,approved_at AS approvedAt,applied_by AS appliedBy,applied_at AS appliedAt,remark
            FROM dp_field_standard_bind_plan ORDER BY id DESC LIMIT 200
            """)
    List<Map<String, Object>> list();

    @Select("""
            SELECT id,name,status,CAST(bindings_json AS CHAR) AS bindingsJson,create_by AS createBy,create_time AS createTime,
                   approved_by AS approvedBy,approved_at AS approvedAt,applied_by AS appliedBy,applied_at AS appliedAt,remark
            FROM dp_field_standard_bind_plan WHERE id=#{id}
            """)
    Map<String, Object> find(@Param("id") Long id);

    @Insert("""
            INSERT INTO dp_field_standard_bind_plan(name,status,bindings_json,create_by,remark)
            VALUES(#{name},'DRAFT',CAST(#{bindingsJson} AS JSON),#{createBy},#{remark})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String, Object> row);

    @Update("""
            UPDATE dp_field_standard_bind_plan SET status='APPROVED',approved_by=#{actor},approved_at=NOW(3)
            WHERE id=#{id} AND status='DRAFT'
            """)
    int approve(@Param("id") Long id, @Param("actor") String actor);

    @Update("""
            UPDATE dp_field_standard_bind_plan SET status='APPLIED',applied_by=#{actor},applied_at=NOW(3)
            WHERE id=#{id} AND status='APPROVED'
            """)
    int markApplied(@Param("id") Long id, @Param("actor") String actor);

    @Update("""
            UPDATE dp_field_standard_bind_plan SET status='REJECTED',approved_by=#{actor},approved_at=NOW(3),remark=#{remark}
            WHERE id=#{id} AND status='DRAFT'
            """)
    int reject(@Param("id") Long id, @Param("actor") String actor, @Param("remark") String remark);
}
