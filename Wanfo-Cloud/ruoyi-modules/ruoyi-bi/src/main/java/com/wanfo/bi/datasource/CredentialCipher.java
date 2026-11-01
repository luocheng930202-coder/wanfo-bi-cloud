package com.wanfo.bi.datasource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Datasource credential crypto. New writes use versioned AES-GCM ({@code encgv:{n}:}).
 * Still decrypts legacy {@code encg:} (treated as v1) and {@code enc:} (ECB with v1).
 */
@Component
public class CredentialCipher {
    private static final Logger log = LoggerFactory.getLogger(CredentialCipher.class);
    private static final String ALGORITHM = "AES";
    private static final String ECB = "AES/ECB/PKCS5Padding";
    private static final String GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String PREFIX_ECB = "enc:";
    private static final String PREFIX_GCM = "encg:";
    private static final String PREFIX_GCM_VER = "encgv:";
    private static final Pattern VERSIONED = Pattern.compile("^encgv:(\\d+):(.+)$");

    private final CredentialKeyProvider keys;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public CredentialCipher(CredentialKeyProvider keys) {
        this.keys = keys;
    }

    /** Convenience for tests / single-key bootstraps (active version = 1). */
    public CredentialCipher(String rawKey) {
        this(new SingleKeyCredentialKeyProvider(rawKey));
    }

    /** Encrypt with the active key version. */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        return encryptWithVersion(plain, keys.activeVersion());
    }

    public String encryptWithVersion(String plain, int version) {
        if (plain == null || plain.isEmpty()) return plain;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(GCM);
            cipher.init(Cipher.ENCRYPT_MODE, secret(version), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
            buffer.put(iv);
            buffer.put(cipherBytes);
            return PREFIX_GCM_VER + version + ":" + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("凭据加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypt. Compatible: plaintext, {@code enc:} ECB(v1), {@code encg:} GCM(v1), {@code encgv:n:} GCM(n).
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) return cipherText;
        Matcher ver = VERSIONED.matcher(cipherText);
        if (ver.matches()) return decryptGcm(ver.group(2), Integer.parseInt(ver.group(1)));
        if (cipherText.startsWith(PREFIX_GCM)) return decryptGcm(cipherText.substring(PREFIX_GCM.length()), 1);
        if (cipherText.startsWith(PREFIX_ECB)) return decryptEcb(cipherText.substring(PREFIX_ECB.length()));
        return cipherText;
    }

    /** Rewrap any decryptable value to the active versioned GCM envelope. */
    public String rewrapToActive(String cipherText) {
        String plain = decrypt(cipherText);
        if (plain == null || plain.isEmpty()) return plain;
        String target = encrypt(plain);
        if (cipherText != null && cipherText.equals(target)) return cipherText;
        return target;
    }

    /** @deprecated use {@link #rewrapToActive(String)}; kept for callers migrating from ECB→GCM. */
    public String rewrapToGcm(String cipherText) {
        return rewrapToActive(cipherText);
    }

    public int activeVersion() {
        return keys.activeVersion();
    }

    public String providerId() {
        return keys.providerId();
    }

    public Map<String, Object> envelopeInfo(String cipherText) {
        Map<String, Object> info = new LinkedHashMap<>();
        if (cipherText == null || cipherText.isEmpty()) {
            info.put("kind", "EMPTY");
            return info;
        }
        Matcher ver = VERSIONED.matcher(cipherText);
        if (ver.matches()) {
            info.put("kind", "GCM_VERSIONED");
            info.put("version", Integer.parseInt(ver.group(1)));
            return info;
        }
        if (cipherText.startsWith(PREFIX_GCM)) {
            info.put("kind", "GCM_LEGACY");
            info.put("version", 1);
            return info;
        }
        if (cipherText.startsWith(PREFIX_ECB)) {
            info.put("kind", "ECB_LEGACY");
            info.put("version", 1);
            return info;
        }
        info.put("kind", "PLAINTEXT");
        return info;
    }

    private String decryptGcm(String encoded, int version) {
        try {
            byte[] payload = Base64.getDecoder().decode(encoded);
            if (payload.length <= GCM_IV_LENGTH) throw new IllegalArgumentException("GCM 密文过短");
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] body = new byte[payload.length - GCM_IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(payload, GCM_IV_LENGTH, body, 0, body.length);
            Cipher cipher = Cipher.getInstance(GCM);
            cipher.init(Cipher.DECRYPT_MODE, secret(version), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("GCM 凭据解密失败 version={}: {}", version, e.getMessage());
            throw new IllegalStateException("凭据解密失败，请检查 bi.credential.key / keys." + version + " 配置", e);
        }
    }

    private String decryptEcb(String encoded) {
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance(ECB);
            cipher.init(Cipher.DECRYPT_MODE, secret(1));
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("ECB 凭据解密失败: {}", e.getMessage());
            throw new IllegalStateException("凭据解密失败，请检查 bi.credential.key 配置", e);
        }
    }

    private SecretKey secret(int version) {
        return new SecretKeySpec(keys.keyMaterial(version), ALGORITHM);
    }

    public static String mask(String value) {
        if (value == null || value.length() <= 4) return value == null ? null : "****";
        int keep = value.length() / 4;
        StringBuilder sb = new StringBuilder();
        sb.append(value, 0, keep);
        for (int i = 0; i < value.length() - keep * 2; i++) sb.append('*');
        sb.append(value, value.length() - keep, value.length());
        return sb.toString();
    }
}
