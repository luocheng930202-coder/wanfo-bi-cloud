package com.wanfo.bi.service;

import java.util.*;
import com.wanfo.bi.domain.BiDashboardShareDO;
import com.wanfo.bi.mapper.BiDashboardShareMapper;
import com.wanfo.bi.security.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import tools.jackson.databind.ObjectMapper;

class SharePasswordGuardTest {
    BiDashboardShareMapper mapper=mock(BiDashboardShareMapper.class);DashboardService boards=mock(DashboardService.class);BiResourceAuthorizer resources=mock(BiResourceAuthorizer.class);CapabilityProvider caps=mock(CapabilityProvider.class);
    BiActor viewer=new BiActor("test",2L,"viewer",null,Set.of());
    DashboardShareService service=new DashboardShareService(mapper,boards,resources,caps,()->viewer,new ObjectMapper(),ref->new LiveActorResolver.Snapshot(ref,Set.of("*:*:*")));
    BiDashboardShareDO share(String hash){var r=new BiDashboardShareDO();r.setId(3L);r.setDashboardId(5L);r.setDataScope("viewer");r.setPasswordHash(hash);when(mapper.selectByToken("token")).thenReturn(r);when(boards.shareView(5L)).thenReturn(new HashMap<>());return r;}
    @Test void oldPasswordsUpgradeOnlyAfterSuccessfulVerification()throws Exception{share(SecurityDigest.hash("secret"));service.view("token","secret");verify(mapper).upgradePassword(eq(3L),eq(SecurityDigest.hash("secret")),argThat(v->v.startsWith("{bcrypt-sha256}")));verify(mapper).auditAccess(3L,2L,"VIEW");verify(mapper,never()).passwordAttempt(anyLong(),anyLong());}
    @Test void wrongPasswordsAreCountedAndAudited(){share(SecurityDigest.hash("secret"));assertEquals(401,assertThrows(BiAccessException.class,()->service.view("token","wrong")).getCode());verify(mapper).passwordAttempt(3L,2L);verify(mapper).auditAccess(3L,2L,"PASSWORD_REJECTED");verifyNoInteractions(boards);}
    @Test void throttleIsPerViewerAndBlocksEvenCorrectPassword(){share(SecurityDigest.hash("secret"));when(mapper.passwordAttempts(3L,2L)).thenReturn(5);assertEquals(429,assertThrows(BiAccessException.class,()->service.view("token","secret")).getCode());verify(mapper).auditAccess(3L,2L,"THROTTLED");verify(mapper,never()).upgradePassword(anyLong(),anyString(),anyString());verifyNoInteractions(boards);}
    @Test void challengeWithoutPasswordDoesNotSpendFailureBudget(){share(SecurityDigest.hash("secret"));assertEquals(401,assertThrows(BiAccessException.class,()->service.view("token",null)).getCode());verify(mapper,never()).passwordAttempt(anyLong(),anyLong());}
    @Test void multiByteAndLongPasswordsDoNotShareBcryptTruncation(){String prefix="长".repeat(40);String hash=DashboardShareService.hashPassword(prefix+"A");assertTrue(DashboardShareService.matchesPassword(prefix+"A",hash));assertFalse(DashboardShareService.matchesPassword(prefix+"B",hash));}
}
