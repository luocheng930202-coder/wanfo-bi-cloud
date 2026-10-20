package com.wanfo.bi.security;

import com.wanfo.bi.mapper.BiPrincipalMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabasePrincipalDirectoryTest
{
    @Test
    void sameExternalNumberIsIsolatedBySourceAndType()
    {
        BiPrincipalMapper mapper = mock(BiPrincipalMapper.class);
        when(mapper.find("ruoyi", "user", "1")).thenReturn(10L);
        when(mapper.find("ruoyi", "role", "1")).thenReturn(20L);
        when(mapper.find("standalone", "user", "1")).thenReturn(30L);
        PrincipalDirectory directory = new DatabasePrincipalDirectory(mapper);
        assertEquals(10L, directory.resolve("ruoyi", "user", "1", "admin"));
        assertEquals(20L, directory.resolve("ruoyi", "role", "1", "admin"));
        assertEquals(30L, directory.resolve("standalone", "user", "1", "admin"));
        verify(mapper, never()).insert(any(), any(), any(), any());
    }

    @Test
    void firstAccessReadsStableIdAfterInsertAndRejectsInvalidKeys()
    {
        BiPrincipalMapper mapper = mock(BiPrincipalMapper.class);
        when(mapper.find("ruoyi", "user", "42")).thenReturn(null, 90L);
        PrincipalDirectory directory = new DatabasePrincipalDirectory(mapper);
        assertEquals(90L, directory.resolve("ruoyi", "user", "42", "alice"));
        verify(mapper).insert("ruoyi", "user", "42", "alice");
        assertThrows(IllegalArgumentException.class, () -> directory.resolve("", "user", "1", "name"));
        assertThrows(IllegalArgumentException.class, () -> directory.resolve("ruoyi", "unknown", "1", "name"));
        assertThrows(IllegalArgumentException.class, () -> directory.resolve("ruoyi", "role", "", "name"));
    }
}
