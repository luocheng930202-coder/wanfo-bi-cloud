package com.wanfo.bi.domain;

import java.util.Date;

import com.wanfo.bi.datasource.BiDatasource;

/**
 * 数据源持久化对象，对应表 bi_datasource。
 *
 * <p>继承 {@link BiDatasource}（领域模型），补充审计与运行参数字段。
 *
 * @author zhengliang
 */
public class BiDatasourceDO extends BiDatasource
{
    private static final long serialVersionUID = 1L;

    /** 连接池最大连接数 */
    private Integer poolMaxTotal;

    /** 连接池最小空闲 */
    private Integer poolMinIdle;

    /** 查询超时(秒) */
    private Integer queryTimeout;

    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private String delFlag;

    public Integer getPoolMaxTotal() { return poolMaxTotal; }
    public void setPoolMaxTotal(Integer poolMaxTotal) { this.poolMaxTotal = poolMaxTotal; }

    public Integer getPoolMinIdle() { return poolMinIdle; }
    public void setPoolMinIdle(Integer poolMinIdle) { this.poolMinIdle = poolMinIdle; }

    public Integer getQueryTimeout() { return queryTimeout; }
    public void setQueryTimeout(Integer queryTimeout) { this.queryTimeout = queryTimeout; }

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

