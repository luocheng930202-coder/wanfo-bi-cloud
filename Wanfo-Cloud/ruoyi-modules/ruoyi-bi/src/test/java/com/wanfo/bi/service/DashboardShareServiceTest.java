package com.wanfo.bi.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DashboardShareServiceTest
{
    @Test void passwordHashIsSaltedAndVerifiable()
    {
        String a = DashboardShareService.hashPassword("secret");
        assertNotNull(a);
        assertNotEquals(a, DashboardShareService.hashPassword("secret"));
        assertTrue(DashboardShareService.matchesPassword("secret",a));assertFalse(DashboardShareService.matchesPassword("wrong",a));
        assertTrue(DashboardShareService.matchesPassword("secret",com.wanfo.bi.security.SecurityDigest.hash("secret")));
        assertNull(DashboardShareService.hashPassword(""));
    }
}
