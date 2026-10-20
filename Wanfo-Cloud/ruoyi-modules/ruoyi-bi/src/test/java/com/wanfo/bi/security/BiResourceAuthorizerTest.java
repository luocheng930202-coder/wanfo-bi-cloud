package com.wanfo.bi.security;

import java.util.List;
import java.util.Set;
import com.wanfo.bi.mapper.BiResourceMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BiResourceAuthorizerTest
{
    private final BiResourceMapper mapper = mock(BiResourceMapper.class);
    private final CapabilityProvider capabilities = mock(CapabilityProvider.class);
    private final CurrentActorProvider actors = () -> new BiActor("ruoyi", 10L, "alice", 20L, Set.of(30L));
    private final BiResourceAuthorizer policy = new BiResourceAuthorizer(mapper, actors, capabilities);

    @Test void defaultsDenyAndLegacyResourceDoesNotTrustHostIds()
    {
        when(mapper.granted("datasource", 7L, "view")).thenReturn(List.of(1L));
        assertFalse(policy.allowed("datasource", 7L, "view"));
        assertThrows(BiAccessException.class, () -> policy.require("datasource", 7L, "edit"));
    }

    @Test void ownerAndExplicitUserRoleOrganizationGrantsWork()
    {
        when(mapper.owner("datasource", 1L)).thenReturn(10L);
        assertTrue(policy.allowed("datasource", 1L, "grant"));
        for (long principal : new long[]{10L,20L,30L})
        {
            when(mapper.granted("datasource", 7L, "view")).thenReturn(List.of(principal));
            assertTrue(policy.allowed("datasource", 7L, "view"));
        }
        assertFalse(policy.allowed("datasource", 7L, "edit"));
    }

    @Test void explicitAdministratorWorksAndUnknownActionsCannotBeGranted()
    {
        when(capabilities.has("bi:resource:admin")).thenReturn(true);
        assertTrue(policy.allowed("datasource", 7L, "view"));
        assertThrows(IllegalArgumentException.class, () -> policy.change("datasource", 7L, 10L, "*", true));
        verify(mapper, never()).grant(any(),any(),any(),any());
    }

    @Test void revokeIsCheckedOnEveryRequest()
    {
        when(mapper.granted("datasource", 7L, "view")).thenReturn(List.of(30L), List.of());
        assertTrue(policy.allowed("datasource", 7L, "view"));
        assertFalse(policy.allowed("datasource", 7L, "view"));
    }
}
