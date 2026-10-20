package com.wanfo.bi.domain;

import java.util.Date;

public class BiDataFormRecordDO
{
    private Long id;
    private Long formId;
    private String rowJson;
    private String success;
    private String errorMsg;
    private String createBy;
    private Date createTime;
    private Long reviewBy;private String reviewNote;private Date reviewTime;
    public Long getReviewBy(){return reviewBy;}public void setReviewBy(Long v){reviewBy=v;}
    public String getReviewNote(){return reviewNote;}public void setReviewNote(String v){reviewNote=v;}
    public Date getReviewTime(){return reviewTime;}public void setReviewTime(Date v){reviewTime=v;}

    private Long ownerPrincipal;private String requestKey,payloadHash,submissionStatus;
    public Long getOwnerPrincipal(){return ownerPrincipal;}public void setOwnerPrincipal(Long v){ownerPrincipal=v;}
    public String getRequestKey(){return requestKey;}public void setRequestKey(String v){requestKey=v;}
    public String getPayloadHash(){return payloadHash;}public void setPayloadHash(String v){payloadHash=v;}
    public String getSubmissionStatus(){return submissionStatus;}public void setSubmissionStatus(String v){submissionStatus=v;}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFormId() { return formId; }
    public void setFormId(Long formId) { this.formId = formId; }
    public String getRowJson() { return rowJson; }
    public void setRowJson(String rowJson) { this.rowJson = rowJson; }
    public String getSuccess() { return success; }
    public void setSuccess(String success) { this.success = success; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
