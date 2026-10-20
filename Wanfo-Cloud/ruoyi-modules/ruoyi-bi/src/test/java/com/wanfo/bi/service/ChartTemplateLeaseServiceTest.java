package com.wanfo.bi.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.wanfo.bi.mapper.BiChartMapper;
import com.wanfo.bi.mapper.BiChartTemplateLeaseMapper;
import com.wanfo.bi.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
@ExtendWith(MockitoExtension.class)
class ChartTemplateLeaseServiceTest {
 @Mock BiChartTemplateLeaseMapper mapper;@Mock BiChartMapper charts;@Mock CapabilityProvider caps;@Mock CurrentActorProvider actors;
 @InjectMocks ChartTemplateLeaseService service;
 @BeforeEach void actor(){when(actors.currentActor()).thenReturn(new BiActor("ruoyi",3L,"u",null,Set.of()));lenient().when(caps.has(anyString())).thenReturn(true);}
 @Test void releaseDeletesLeasedChart(){when(mapper.find(3L,8L)).thenReturn(8L);when(charts.delete(8L,"u")).thenReturn(1);var r=service.release(new ChartTemplateLeaseService.Release(List.of(8L)));assertEquals(1,r.get("released"));verify(mapper).delete(3L,8L);}
 @Test void commitDropsLeaseRows(){service.commit(new ChartTemplateLeaseService.Release(List.of(1L,2L)));verify(mapper).deleteMany(3L,List.of(1L,2L));}
}
