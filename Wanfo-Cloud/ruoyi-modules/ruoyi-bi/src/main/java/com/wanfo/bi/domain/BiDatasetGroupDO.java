package com.wanfo.bi.domain;

import java.util.Date;

/**
 * 数据集目录，对应表 bi_dataset_group。
 *
 * @author zhengliang
 */
public class BiDatasetGroupDO
{
    private Long id;
    private Long parentId;
    /** 祖级列表，逗号分隔，如 0,1,5 */
    private String ancestors;
    private String name;
    private Integer orderNum;
    private String status;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public String getAncestors() { return ancestors; }
    public void setAncestors(String ancestors) { this.ancestors = ancestors; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getOrderNum() { return orderNum; }
    public void setOrderNum(Integer orderNum) { this.orderNum = orderNum; }

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

