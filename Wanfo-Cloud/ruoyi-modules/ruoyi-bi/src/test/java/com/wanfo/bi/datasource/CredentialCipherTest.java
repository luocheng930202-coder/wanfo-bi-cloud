package com.wanfo.bi.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class CredentialCipherTest
{
    private static CredentialCipher cipher(String key, int active, Map<Integer, String> extras)
    {
        MockEnvironment env = new MockEnvironment();
        if (extras != null) extras.forEach((ver, value) -> env.setProperty("bi.credential.keys." + ver, value));
        LocalConfigCredentialKeyProvider provider = new LocalConfigCredentialKeyProvider(key, active, env);
        return new CredentialCipher(provider);
    }

    @Test
    void encryptUsesVersionedGcmPrefixAndRoundTrips()
    {
        CredentialCipher cipher = cipher("unit-test-secret-key", 1, Map.of());
        String encrypted = cipher.encrypt("s3cret!");
        assertTrue(encrypted.startsWith("encgv:1:"));
        assertEquals("s3cret!", cipher.decrypt(encrypted));
        assertEquals(1, cipher.envelopeInfo(encrypted).get("version"));
    }

    @Test
    void rewrapUpgradesLegacyPlainAndVersionedTarget()
    {
        CredentialCipher cipher = cipher("unit-test-secret-key", 1, Map.of());
        String gcm = cipher.encrypt("legacy-pass");
        assertEquals("legacy-pass", cipher.decrypt(gcm));
        String fromPlain = cipher.rewrapToGcm("plain-password");
        assertTrue(fromPlain.startsWith("encgv:1:"));
        assertEquals("plain-password", cipher.decrypt(fromPlain));
    }

    @Test
    void rotateToSecondKeyVersion()
    {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("bi.credential.keys.2", "unit-test-rotated-key");
        LocalConfigCredentialKeyProvider v1 = new LocalConfigCredentialKeyProvider("unit-test-secret-key", 1, env);
        CredentialCipher writer = new CredentialCipher(v1);
        String oldEnvelope = writer.encrypt("rotate-me");
        assertTrue(oldEnvelope.startsWith("encgv:1:"));

        LocalConfigCredentialKeyProvider v2 = new LocalConfigCredentialKeyProvider("unit-test-secret-key", 2, env);
        CredentialCipher rotator = new CredentialCipher(v2);
        assertEquals("rotate-me", rotator.decrypt(oldEnvelope));
        String next = rotator.encryptWithVersion(rotator.decrypt(oldEnvelope), 2);
        assertTrue(next.startsWith("encgv:2:"));
        assertEquals("rotate-me", rotator.decrypt(next));
        assertEquals(List.of("AVAILABLE", "ACTIVE"),
                v2.describeVersions().stream().map(r -> String.valueOf(r.get("status"))).toList());
    }
}
