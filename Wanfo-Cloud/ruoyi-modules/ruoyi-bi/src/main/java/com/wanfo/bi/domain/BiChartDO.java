package com.wanfo.bi.domain;

public class BiChartDO {
    private Long id,datasetId;
    private String name,type,definitionJson,createBy,updateBy;
    private Integer version;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getDatasetId(){return datasetId;} public void setDatasetId(Long v){datasetId=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getType(){return type;} public void setType(String v){type=v;}
    public String getDefinitionJson(){return definitionJson;} public void setDefinitionJson(String v){definitionJson=v;}
    public String getCreateBy(){return createBy;} public void setCreateBy(String v){createBy=v;}
    public String getUpdateBy(){return updateBy;} public void setUpdateBy(String v){updateBy=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
}
