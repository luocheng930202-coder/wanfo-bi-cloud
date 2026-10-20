package com.wanfo.bi.domain;

import java.util.Date;

/**
 * 数据集字段，对应表 bi_dataset_field。
 *
 * @author zhengliang
 */
public class BiDatasetFieldDO
{
    private Long id;
    private Long datasetId;
    /** 原始字段名（SQL 里引用这个） */
    private String originName;
    /** 原始数据类型 */
    private String originType;
    /** 前端显示名 */
    private String showName;
    /** 1=维度 0=度量 */
    private String dimFlag;
    /** 1=计算字段 0=物理字段 */
    private String extFlag;
    /** 计算表达式 */
    private String extFormula;
    /** 日期格式化 */
    private String dateFormat;
    /** 业务分组 */
    private String groupName;
    private Integer orderNum;
    private String status;
    private Date createTime;
    private Date updateTime;
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }

    public String getOriginName() { return originName; }
    public void setOriginName(String originName) { this.originName = originName; }

    public String getOriginType() { return originType; }
    public void setOriginType(String originType) { this.originType = originType; }

    public String getShowName() { return showName; }
    public void setShowName(String showName) { this.showName = showName; }

    public String getDimFlag() { return dimFlag; }
    public void setDimFlag(String dimFlag) { this.dimFlag = dimFlag; }

    public String getExtFlag() { return extFlag; }
    public void setExtFlag(String extFlag) { this.extFlag = extFlag; }

    public String getExtFormula() { return extFormula; }
    public void setExtFormula(String extFormula) { this.extFormula = extFormula; }

    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public Integer getOrderNum() { return orderNum; }
    public void setOrderNum(Integer orderNum) { this.orderNum = orderNum; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}

