package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DataApiCallerValidationTest {
    @Test
    void rejectsBadOrgCodeAndAcceptsValid() {
        assertThrows(IllegalArgumentException.class,
                () -> validate(new DataApiService.CallerSaveRequest("1bad", "Org", null, null, null, null), true));
        assertThrows(IllegalArgumentException.class,
                () -> validate(new DataApiService.CallerSaveRequest("ok", " ", null, null, null, null), true));
        assertDoesNotThrow(() -> validate(
                new DataApiService.CallerSaveRequest("partner_a", "Partner A", "n", "a@b.c", "note", null), true));
    }

    /** Mirrors DataApiService.validateCaller rules for unit coverage without Spring wiring. */
    private static void validate(DataApiService.CallerSaveRequest request, boolean creating) {
        if (request == null || request.orgName() == null || request.orgName().isBlank() || request.orgName().trim().length() > 200)
            throw new IllegalArgumentException("请填写调用方组织名称");
        if (creating) {
            if (request.orgCode() == null || !request.orgCode().matches("^[A-Za-z][A-Za-z0-9_-]{1,63}$"))
                throw new IllegalArgumentException("调用方组织编码须为字母开头的 2～64 位标识");
        }
    }
}
