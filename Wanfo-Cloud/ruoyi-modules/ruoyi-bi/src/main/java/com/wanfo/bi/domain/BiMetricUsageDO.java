package com.wanfo.bi.domain;

import java.util.Date;

public class BiMetricUsageDO {
    private Long id, metricId;
    private String refType, refId, remark, createBy;
    private Date createTime;
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getMetricId() { return metricId; } public void setMetricId(Long v) { metricId = v; }
    public String getRefType() { return refType; } public void setRefType(String v) { refType = v; }
    public String getRefId() { return refId; } public void setRefId(String v) { refId = v; }
    public String getRemark() { return remark; } public void setRemark(String v) { remark = v; }
    public String getCreateBy() { return createBy; } public void setCreateBy(String v) { createBy = v; }
    public Date getCreateTime() { return createTime; } public void setCreateTime(Date v) { createTime = v; }
}
