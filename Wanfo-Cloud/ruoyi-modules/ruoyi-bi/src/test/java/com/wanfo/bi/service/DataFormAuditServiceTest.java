package com.wanfo.bi.service;

import com.wanfo.bi.domain.BiDataFormRecordDO;
import com.wanfo.bi.mapper.BiDataFormRecordMapper;
import com.wanfo.bi.security.BiAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataFormAuditServiceTest {
    BiDataFormRecordMapper mapper=mock(BiDataFormRecordMapper.class);
    DataFormAuditService service=new DataFormAuditService(mapper);
    BiDataFormRecordDO record(String hash) {var row=new BiDataFormRecordDO();row.setFormId(1L);row.setOwnerPrincipal(2L);row.setRequestKey("request_1234567890");row.setPayloadHash(hash);return row;}
    @Test void firstReservationIsFresh() {assertTrue(service.reserve(record("a")).fresh());verify(mapper).insert(any());}
    @Test void duplicateReturnsExistingIntentInsteadOfWritingAgain() {
        var stored=record("a");stored.setSubmissionStatus("pending");
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));when(mapper.findSubmission(1L,2L,"request_1234567890")).thenReturn(stored);
        var reservation=service.reserve(record("a"));assertFalse(reservation.fresh());assertSame(stored,reservation.record());
    }
    @Test void reusedKeyWithDifferentValuesIsRejected() {
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));when(mapper.findSubmission(1L,2L,"request_1234567890")).thenReturn(record("a"));
        assertEquals(409,assertThrows(BiAccessException.class,()->service.reserve(record("b"))).getCode());
    }
    @Test void finishOnlyTransitionsPendingIntent() {
        when(mapper.finishSubmission(1L,"succeeded",null)).thenReturn(1,0);service.finish(1L,"succeeded",null);
        assertThrows(BiAccessException.class,()->service.finish(1L,"succeeded",null));
    }
}
