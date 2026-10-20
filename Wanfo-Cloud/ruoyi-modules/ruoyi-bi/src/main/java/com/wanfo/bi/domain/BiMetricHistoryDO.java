package com.wanfo.bi.domain;

import java.util.Date;

public class BiMetricHistoryDO {
    private Long id, metricId, datasetId;
    private Integer publishedVersion;
    private String code, name, kind, definitionJson, publishBy;
    private Date publishTime;
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getMetricId() { return metricId; } public void setMetricId(Long v) { metricId = v; }
    public Long getDatasetId() { return datasetId; } public void setDatasetId(Long v) { datasetId = v; }
    public Integer getPublishedVersion() { return publishedVersion; } public void setPublishedVersion(Integer v) { publishedVersion = v; }
    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public String getKind() { return kind; } public void setKind(String v) { kind = v; }
    public String getDefinitionJson() { return definitionJson; } public void setDefinitionJson(String v) { definitionJson = v; }
    public String getPublishBy() { return publishBy; } public void setPublishBy(String v) { publishBy = v; }
    public Date getPublishTime() { return publishTime; } public void setPublishTime(Date v) { publishTime = v; }
}
