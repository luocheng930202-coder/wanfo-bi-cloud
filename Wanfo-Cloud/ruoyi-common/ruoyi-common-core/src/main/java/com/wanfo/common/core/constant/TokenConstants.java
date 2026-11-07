package com.wanfo.common.core.constant;

/**
 * Token的Key常量
 * 
 * @author ruoyi
 */
public class TokenConstants
{
    /**
     * 令牌前缀
     */
    public static final String PREFIX = "Bearer ";

    /**
     * 令牌秘钥。由部署环境注入，IDEA 在运行配置中设置同一变量。
     */
    public final static String SECRET = System.getenv("WANFO_JWT_SECRET");

}
