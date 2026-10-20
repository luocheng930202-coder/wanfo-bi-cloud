package com.wanfo.bi.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.wanfo.bi.mapper.BiImageStagingMapper;
import com.wanfo.bi.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
@ExtendWith(MockitoExtension.class)
class AssetStagingServiceTest {
 @Mock BiImageStagingMapper mapper;@Mock AssetStorage storage;@Mock BiResourceAuthorizer resources;@Mock CapabilityProvider caps;@Mock CurrentActorProvider actors;
 @InjectMocks AssetStagingService service;
 private static final String PNG="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1ZkAAAAASUVORK5CYII=";
 @BeforeEach void actor(){when(actors.currentActor()).thenReturn(new BiActor("ruoyi",9L,"u",null,Set.of()));lenient().when(caps.has(anyString())).thenReturn(true);}
 @Test void uploadTracksOwner(){when(mapper.countActive(9L)).thenReturn(0);doAnswer(inv->{Map<String,Object> row=inv.getArgument(0);row.put("id",3L);return 1;}).when(mapper).insert(anyMap());var r=service.upload(new AssetStagingService.Upload("a",PNG));assertEquals(3L,r.get("id"));assertEquals(PNG,r.get("image"));}
 @Test void releaseOnlyOwnerBatch(){service.release(new AssetStagingService.Release(List.of(1L,2L)));verify(mapper).release(9L,List.of(1L,2L));}
 @Test void promoteCreatesLibraryAsset(){when(mapper.get(4L,9L)).thenReturn(Map.of("name","x","image",PNG,"byteSize",70));when(storage.save("x",PNG,70,"u")).thenReturn(11L);var r=service.promote(4L,null);assertEquals(11L,r.get("id"));verify(resources).register("asset",11L);verify(mapper).release(9L,List.of(4L));}
 @Test void capBlocksWhenTooMany(){when(mapper.countActive(9L)).thenReturn(30);assertThrows(IllegalArgumentException.class,()->service.upload(new AssetStagingService.Upload("a",PNG)));}
}
