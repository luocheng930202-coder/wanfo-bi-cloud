package com.wanfo.bi.domain;

import java.util.Date;

/**
 * 数据集定义，对应表 bi_dataset_table。
 *
 * <p>type 的语义：
 * <ul>
 *   <li>~R~table~R~ — 物理表，~R~sourceTable~R~ 生效</li>
 *   <li>~R~sql~R~   — 手写 SQL，~R~sqlText~R~ 生效</li>
 * </ul>
 *
 * @author zhengliang
 */
public class BiDatasetTableDO
{
    private Long id;
    private Long groupId;
    private String name;
    /** table / sql */
    private String type;
    private Long datasourceId;
    /** type=table 时的物理表名 */
    private String sourceTable;
    private String sourceCatalog;

    public String getSourceCatalog() { return sourceCatalog; }
    public void setSourceCatalog(String sourceCatalog) { this.sourceCatalog = sourceCatalog; }
    /** type=sql 时的原始 SQL */
    private String sqlText;
    /** 系统生成的最终执行 SQL */
    private String unionSql;
    private String cacheEnabled;
    private Integer cacheTtl;
    private String remark;
    /** JSON: join model for table datasets */
    private String modelJson;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private String delFlag;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getDatasourceId() { return datasourceId; }
    public void setDatasourceId(Long datasourceId) { this.datasourceId = datasourceId; }

    public String getSourceTable() { return sourceTable; }
    public void setSourceTable(String sourceTable) { this.sourceTable = sourceTable; }

    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }

    public String getUnionSql() { return unionSql; }
    public void setUnionSql(String unionSql) { this.unionSql = unionSql; }

    public String getCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(String cacheEnabled) { this.cacheEnabled = cacheEnabled; }

    public Integer getCacheTtl() { return cacheTtl; }
    public void setCacheTtl(Integer cacheTtl) { this.cacheTtl = cacheTtl; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getModelJson() { return modelJson; }
    public void setModelJson(String modelJson) { this.modelJson = modelJson; }

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

