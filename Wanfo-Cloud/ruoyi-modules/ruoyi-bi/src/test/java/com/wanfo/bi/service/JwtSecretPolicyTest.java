package com.wanfo.bi.service;

import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.wanfo.common.core.utils.JwtUtils;
import static org.junit.jupiter.api.Assertions.*;

class JwtSecretPolicyTest {
    private void withSecret(String value, Runnable check) {
        String previous = System.getProperty("wanfo.jwt.secret");
        try { System.setProperty("wanfo.jwt.secret", value); check.run(); }
        finally { if (previous == null) System.clearProperty("wanfo.jwt.secret"); else System.setProperty("wanfo.jwt.secret", previous); }
    }
    @Test void rejectsBlankWeakAndMalformedSecrets() {
        for (String value : new String[]{"", "not base64!", Base64.getEncoder().encodeToString(new byte[16])}) {
            withSecret(value, () -> assertThrows(IllegalStateException.class, () -> JwtUtils.createToken(Map.of("sub", "test"))));
        }
    }
    @Test void randomStrongSecretRoundTripsAndRotationRejectsOldTokens() {
        byte[] bytes = new byte[64]; new java.security.SecureRandom().nextBytes(bytes);
        String first = Base64.getEncoder().encodeToString(bytes);
        withSecret(first, () -> {
            String token = JwtUtils.createToken(Map.of("sub", "test"));
            assertEquals("test", JwtUtils.parseToken(token).getSubject());
            new java.security.SecureRandom().nextBytes(bytes);
            withSecret(Base64.getEncoder().encodeToString(bytes), () -> assertThrows(io.jsonwebtoken.SignatureException.class, () -> JwtUtils.parseToken(token)));
        });
    }
}
