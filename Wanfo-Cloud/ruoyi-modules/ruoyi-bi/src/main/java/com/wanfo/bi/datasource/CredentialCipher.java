package com.wanfo.bi.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 数据源凭据加解密。
 *
 * <p>若依自身用 jasypt 加密配置文件里的密码，但 BI 的数据源密码是<b>运行时由用户录入并落库</b>的，
 * 需要独立的加解密方案。
 *
 * <p>当前用 AES/ECB + Base64，满足"不能明文落库"的底线要求。
 * TODO(S3-T06)：ECB 相同明文产生相同密文，后续应换成 AES/GCM 或改用 KMS。
 *
 * @author zhengliang
 */
@Component
public class CredentialCipher
{
    private static final Logger log = LoggerFactory.getLogger(CredentialCipher.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /**
     * AES 要求密钥必须是 16/24/32 字节，而配置里的原始字符串长度是任意的
     * （默认值 zhengliang-bi-2026-secret 就有 25 字节）。
     * 这里统一用 SHA-256 派生出固定 32 字节（AES-256），既满足长度约束，
     * 也让配置者不必关心字节数；同时避免弱密钥被直接使用。
     */
    private final byte[] key;

    public CredentialCipher(@Value("${bi.credential.key:zhengliang-bi-2026-secret}") String rawKey)
    {
        try
        {
            this.key = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(rawKey.getBytes(StandardCharsets.UTF_8));
        }
        catch (java.security.NoSuchAlgorithmException e)
        {
            // SHA-256 是 JDK 必备算法，走到这里说明 JRE 异常
            throw new IllegalStateException("无法初始化凭据加密密钥", e);
        }
    }

    /** 加密，空值原样返回 */
    public String encrypt(String plain)
    {
        if (plain == null || plain.isEmpty())
        {
            return plain;
        }
        try
        {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return "enc:" + Base64.getEncoder().encodeToString(enc);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("凭据加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解密。
     * <p>兼容历史明文：没有 enc: 前缀的直接原样返回，
     * 这样迁移期新旧数据混存也不会炸。
     */
    public String decrypt(String cipherText)
    {
        if (cipherText == null || cipherText.isEmpty())
        {
            return cipherText;
        }
        if (!cipherText.startsWith("enc:"))
        {
            return cipherText;
        }
        try
        {
            byte[] data = Base64.getDecoder().decode(cipherText.substring(4));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM));
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            log.warn("凭据解密失败，可能密钥已变更: {}", e.getMessage());
            throw new IllegalStateException("凭据解密失败，请检查 bi.credential.key 配置", e);
        }
    }

    /** 脱敏展示：138****8000 */
    public static String mask(String value)
    {
        if (value == null || value.length() <= 4)
        {
            return value == null ? null : "****";
        }
        int keep = value.length() / 4;
        StringBuilder sb = new StringBuilder();
        sb.append(value, 0, keep);
        for (int i = 0; i < value.length() - keep * 2; i++)
        {
            sb.append('*');
        }
        sb.append(value, value.length() - keep, value.length());
        return sb.toString();
    }
}

