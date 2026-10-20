package com.wanfo.bi.mapper;

import java.util.List;

import com.wanfo.bi.domain.BiDataFormRecordDO;

public interface BiDataFormRecordMapper
{
    @org.apache.ibatis.annotations.Select("SELECT id,form_id AS formId,submission_status AS status FROM bi_data_form_record WHERE id=#{id} AND form_id=#{form}")
    java.util.Map<String,Object> reviewTarget(@org.apache.ibatis.annotations.Param("form") Long form,@org.apache.ibatis.annotations.Param("id") Long id);
    @org.apache.ibatis.annotations.Update("UPDATE bi_data_form_record SET submission_status=#{next},success=IF(#{next}='succeeded','1','0'),review_by=#{reviewer},review_note=#{note},review_time=NOW() WHERE id=#{id} AND form_id=#{form} AND submission_status=#{previous}")
    int reviewSubmission(@org.apache.ibatis.annotations.Param("form") Long form,@org.apache.ibatis.annotations.Param("id") Long id,@org.apache.ibatis.annotations.Param("previous") String previous,@org.apache.ibatis.annotations.Param("next") String next,@org.apache.ibatis.annotations.Param("reviewer") Long reviewer,@org.apache.ibatis.annotations.Param("note") String note);
    @org.apache.ibatis.annotations.Insert("INSERT INTO bi_data_form_review_log(record_id,previous_status,resolved_status,reviewer_principal,evidence) VALUES(#{id},#{previous},#{next},#{reviewer},#{note})")
    int appendReview(@org.apache.ibatis.annotations.Param("id") Long id,@org.apache.ibatis.annotations.Param("previous") String previous,@org.apache.ibatis.annotations.Param("next") String next,@org.apache.ibatis.annotations.Param("reviewer") Long reviewer,@org.apache.ibatis.annotations.Param("note") String note);
    @org.apache.ibatis.annotations.Select("SELECT id,previous_status AS previousStatus,resolved_status AS resolvedStatus,reviewer_principal AS reviewerPrincipal,evidence,create_time AS createTime FROM bi_data_form_review_log WHERE record_id=#{id} ORDER BY id DESC LIMIT 100")
    List<java.util.Map<String,Object>> reviewHistory(Long id);
    @org.apache.ibatis.annotations.Update("UPDATE bi_data_form_record SET submission_status=#{status},success=IF(#{status}='succeeded','1','0'),error_msg=#{message} WHERE id=#{id} AND submission_status='pending'")
    int finishSubmission(@org.apache.ibatis.annotations.Param("id") Long id,@org.apache.ibatis.annotations.Param("status") String status,@org.apache.ibatis.annotations.Param("message") String message);
    BiDataFormRecordDO findSubmission(@org.apache.ibatis.annotations.Param("formId") Long formId,@org.apache.ibatis.annotations.Param("owner") Long owner,@org.apache.ibatis.annotations.Param("requestKey") String requestKey);
    List<BiDataFormRecordDO> listVisible(@org.apache.ibatis.annotations.Param("formId") Long formId,@org.apache.ibatis.annotations.Param("owner") Long owner,@org.apache.ibatis.annotations.Param("admin") boolean admin);
    int insert(BiDataFormRecordDO row);

    List<BiDataFormRecordDO> listByFormId(Long formId);
}
