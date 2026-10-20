package com.wanfo.bi.domain;
public class BiDashboardDO {
    private Long id;
    private String name,definitionJson,publishedJson,createBy,updateBy;
    private Integer version,publishedVersion;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getDefinitionJson(){return definitionJson;} public void setDefinitionJson(String v){definitionJson=v;}
    public String getPublishedJson(){return publishedJson;} public void setPublishedJson(String v){publishedJson=v;}
    public String getCreateBy(){return createBy;} public void setCreateBy(String v){createBy=v;}
    public String getUpdateBy(){return updateBy;} public void setUpdateBy(String v){updateBy=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public Integer getPublishedVersion(){return publishedVersion;} public void setPublishedVersion(Integer v){publishedVersion=v;}
}
