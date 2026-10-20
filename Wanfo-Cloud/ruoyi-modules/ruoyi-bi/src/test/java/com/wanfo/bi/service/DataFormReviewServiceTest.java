package com.wanfo.bi.service;

import java.util.*;
import com.wanfo.bi.domain.BiDataFormDO;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.security.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataFormReviewServiceTest {
    BiDataFormMapper forms=mock(BiDataFormMapper.class);BiDataFormRecordMapper records=mock(BiDataFormRecordMapper.class);BiResourceAuthorizer resources=mock(BiResourceAuthorizer.class);LiveActorResolver live=mock(LiveActorResolver.class);
    BiActor actor=new BiActor("ruoyi",1L,"admin",null,Set.of());
    DataFormReviewService service=new DataFormReviewService(forms,records,resources,live,()->ActorScope.active()?ActorScope.actor():actor);
    void fixture(String status){when(live.resolve(actor)).thenReturn(new LiveActorResolver.Snapshot(actor,Set.of("*:*:*")));var form=new BiDataFormDO();form.setDatasetId(5L);when(forms.selectById(1L)).thenReturn(form);when(records.reviewTarget(1L,2L)).thenReturn(Map.of("status",status));when(records.reviewSubmission(eq(1L),eq(2L),anyString(),anyString(),eq(1L),anyString())).thenReturn(1);when(records.appendReview(anyLong(),anyString(),anyString(),anyLong(),anyString())).thenReturn(1);}
    @Test void pendingOnlyAcceptsNotes(){fixture("pending");assertThrows(BiAccessException.class,()->service.review(1L,2L,new DataFormReviewService.Review("pending","failed","已核对源库业务唯一键")));verify(records,never()).reviewSubmission(anyLong(),anyLong(),anyString(),anyString(),anyLong(),anyString());}
    @Test void pendingNoteKeepsStateAndAppendsAudit(){fixture("pending");assertEquals("pending",service.review(1L,2L,new DataFormReviewService.Review("pending","note","已核对源库业务唯一键")).get("status"));verify(records).appendReview(2L,"pending","pending",1L,"已核对源库业务唯一键");assertFalse(ActorScope.active());}
    @Test void uncertainCanBeConfirmedWithEvidence(){fixture("uncertain");assertEquals("succeeded",service.review(1L,2L,new DataFormReviewService.Review("uncertain","succeeded","源库业务唯一键ABC存在，事务已完成")).get("status"));verify(records).appendReview(eq(2L),eq("uncertain"),eq("succeeded"),eq(1L),anyString());}
    @Test void staleStatusRejected(){fixture("succeeded");assertEquals(409,assertThrows(BiAccessException.class,()->service.review(1L,2L,new DataFormReviewService.Review("uncertain","failed","业务唯一键核对未写入"))).getCode());}
    @Test void compareAndSetFailureDoesNotAppendAudit(){fixture("uncertain");when(records.reviewSubmission(anyLong(),anyLong(),anyString(),anyString(),anyLong(),anyString())).thenReturn(0);assertThrows(BiAccessException.class,()->service.review(1L,2L,new DataFormReviewService.Review("uncertain","failed","业务唯一键核对未写入")));verify(records,never()).appendReview(anyLong(),anyString(),anyString(),anyLong(),anyString());}
    @Test void ordinaryUsersCannotReview(){when(live.resolve(actor)).thenReturn(new LiveActorResolver.Snapshot(actor,Set.of("bi:form:list","bi:form:edit")));assertThrows(BiAccessException.class,()->service.history(1L,2L));verifyNoInteractions(records,forms,resources);}
    @Test void evidenceRequiredAndSourceWriteGrantRequired(){fixture("uncertain");assertThrows(IllegalArgumentException.class,()->service.review(1L,2L,new DataFormReviewService.Review("uncertain","failed","")));doThrow(new BiAccessException(403,"denied")).when(resources).require("dataset",5L,"write");assertThrows(BiAccessException.class,()->service.history(1L,2L));}
}
