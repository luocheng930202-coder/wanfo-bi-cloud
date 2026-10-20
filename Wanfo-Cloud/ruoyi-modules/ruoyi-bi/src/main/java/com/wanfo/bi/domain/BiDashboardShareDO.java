package com.wanfo.bi.domain;

import java.util.Date;

public class BiDashboardShareDO
{
    private Long id;
    private Long dashboardId;
    private String token;
    private String dataScope;
    private String passwordHash;
    private Date expireTime;
    private String publisherActorJson;
    private String status;
    private String createBy;
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDashboardId() { return dashboardId; }
    public void setDashboardId(Long dashboardId) { this.dashboardId = dashboardId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Date getExpireTime() { return expireTime; }
    public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }
    public String getPublisherActorJson() { return publisherActorJson; }
    public void setPublisherActorJson(String publisherActorJson) { this.publisherActorJson = publisherActorJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
