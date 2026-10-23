package com.wanfo.bi.datasource;

import java.io.Serializable;

/**
 * BI 数据源定义。
 *
 * <p>对应元数据表 {@code bi_datasource}（S2-T02 落地）。当前阶段先以 POJO 承载，
 * 后续由 MyBatis Mapper 接管，字段结构不变。
 *
 * @author zhengliang
 */
public class BiDatasource implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 数据源主键 */
    private Long id;

    /** 展示名称，如「测试 Doris」 */
    private String name;

    /** 数据源类型：mysql / doris / postgresql / oracle ...（决定方言与驱动） */
    private String type;

    /**
     * Calcite 中的 schema 别名，SQL 里以 {@code schemaName.tableName} 引用。
     * 跨源 JOIN 的最小单位就是 schema。
     */
    private String schemaName;

    /** JDBC 连接串 */
    private String jdbcUrl;
    /** Physical schema inside the configured database; never the Calcite alias. */
    private String sourceSchema;
    public String getSourceSchema(){return sourceSchema;}
    public void setSourceSchema(String value){sourceSchema=value;}

    /** 驱动类名 */
    private String driverClass;

    /** 连接账号 */
    private String username;

    /** 连接密码（落库加密，读取时解密，接口输出必须脱敏） */
    private String password;

    /** SSH 隧道：主机，为空表示不使用 */
    private String sshHost;

    /** SSH 隧道：端口 */
    private Integer sshPort;

    /** SSH 隧道：用户名 */
    private String sshUser;

    /** SSH 隧道：密码（落库加密） */
    private String sshPassword;

    /** SSH 隧道：私钥（落库加密） */
    private String sshPrivateKey;

    /** 备注 */
    private String remark;

    /** 状态：0 正常 1 停用 */
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

    public String getDriverClass() { return driverClass; }
    public void setDriverClass(String driverClass) { this.driverClass = driverClass; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getSshHost() { return sshHost; }
    public void setSshHost(String sshHost) { this.sshHost = sshHost; }

    public Integer getSshPort() { return sshPort; }
    public void setSshPort(Integer sshPort) { this.sshPort = sshPort; }

    public String getSshUser() { return sshUser; }
    public void setSshUser(String sshUser) { this.sshUser = sshUser; }

    public String getSshPassword() { return sshPassword; }
    public void setSshPassword(String sshPassword) { this.sshPassword = sshPassword; }

    public String getSshPrivateKey() { return sshPrivateKey; }
    public void setSshPrivateKey(String sshPrivateKey) { this.sshPrivateKey = sshPrivateKey; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString()
    {
        // 不输出密码
        return "BiDatasource{id=" + id + ", name='" + name + "', type='" + type
                + "', schemaName='" + schemaName + "', jdbcUrl='" + jdbcUrl + "'}";
    }
}

