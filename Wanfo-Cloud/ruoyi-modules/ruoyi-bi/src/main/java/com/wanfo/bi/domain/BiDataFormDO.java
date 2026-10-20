package com.wanfo.bi.domain;

import java.util.Date;

public class BiDataFormDO
{
    private Long id;
    private String name;
    private Long datasetId;
    private String fieldsJson;
    private String remark;
    private String status;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public String getFieldsJson() { return fieldsJson; }
    public void setFieldsJson(String fieldsJson) { this.fieldsJson = fieldsJson; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}
