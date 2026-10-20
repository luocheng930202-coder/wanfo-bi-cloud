package com.wanfo.bi.domain;

public class BiMetricDO {
    private Long id, datasetId;
    private String code, name, kind, definitionJson, publishedJson, publishStatus, createBy, updateBy;
    private Integer version, publishedVersion, usageCount;
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getDatasetId() { return datasetId; } public void setDatasetId(Long v) { datasetId = v; }
    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public String getKind() { return kind; } public void setKind(String v) { kind = v; }
    public String getDefinitionJson() { return definitionJson; } public void setDefinitionJson(String v) { definitionJson = v; }
    public String getPublishedJson() { return publishedJson; } public void setPublishedJson(String v) { publishedJson = v; }
    public String getPublishStatus() { return publishStatus; } public void setPublishStatus(String v) { publishStatus = v; }
    public String getCreateBy() { return createBy; } public void setCreateBy(String v) { createBy = v; }
    public String getUpdateBy() { return updateBy; } public void setUpdateBy(String v) { updateBy = v; }
    public Integer getVersion() { return version; } public void setVersion(Integer v) { version = v; }
    public Integer getPublishedVersion() { return publishedVersion; } public void setPublishedVersion(Integer v) { publishedVersion = v; }
    public Integer getUsageCount() { return usageCount; } public void setUsageCount(Integer v) { usageCount = v; }
}
