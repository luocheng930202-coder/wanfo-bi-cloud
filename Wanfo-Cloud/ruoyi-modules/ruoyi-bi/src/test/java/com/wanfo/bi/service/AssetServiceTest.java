package com.wanfo.bi.service;
import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.wanfo.bi.security.*;
class AssetServiceTest {
 AssetStorage storage=mock(AssetStorage.class);BiResourceAuthorizer resources=mock(BiResourceAuthorizer.class);CapabilityProvider caps=mock(CapabilityProvider.class);
 AssetService service=new AssetService(storage,resources,caps,()->new BiActor("test",1L,"alice",null,Set.of()));
 static String image(int w,int h)throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB),"png",out);return "data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray());}
 @Test void uploadRegistersRealOwnerAndStoresVerifiedImage()throws Exception{String data=image(2,2);when(storage.save(eq("Logo"),eq(data),anyInt(),eq("alice"))).thenReturn(7L);assertEquals(7L,service.upload(new AssetService.Upload(" Logo ",data)).get("id"));verify(resources).register("asset",7L);}
 @Test void nameRequired(){assertThrows(IllegalArgumentException.class,()->service.upload(new AssetService.Upload(" ","")));verifyNoInteractions(storage);}
 @Test void remoteAndSvgDenied(){assertThrows(IllegalArgumentException.class,()->AssetService.validate("https://example.com/a.png"));assertThrows(IllegalArgumentException.class,()->AssetService.validate("data:image/svg+xml;base64,PHN2Zz4="));}
 @Test void fakeSignatureCannotPassDecoder(){assertThrows(IllegalArgumentException.class,()->AssetService.validate("data:image/png;base64,iVBORw0KGgo="));}
 @Test void imageDimensionsBounded()throws Exception{String data=image(4097,1);assertThrows(IllegalArgumentException.class,()->AssetService.validate(data));}
 @Test void readRequiresResourcePermission(){doThrow(new BiAccessException(403,"denied")).when(resources).require("asset",7L,"view");assertThrows(BiAccessException.class,()->service.detail(7L));verifyNoInteractions(storage);}
 @Test void deleteRequiresOwnResourcePermission(){doThrow(new BiAccessException(403,"denied")).when(resources).require("asset",7L,"remove");assertThrows(BiAccessException.class,()->service.delete(7L));verifyNoInteractions(storage);}
 @Test void listFiltersBeforePaginationAndDoesNotExposeBlob(){when(storage.list()).thenReturn(List.of(Map.of("id",1L,"name","private"),Map.of("id",2L,"name","visible")));when(resources.allowed("asset",2L,"view")).thenReturn(true);var result=service.list(1);assertEquals(1,result.get("total"));assertEquals(List.of(Map.of("id",2L,"name","visible","resourceActions",List.of())),result.get("items"));}
 @Test void invalidPageRejected(){assertThrows(IllegalArgumentException.class,()->service.list(0));verifyNoInteractions(storage);}
 @Test void functionPermissionCannotBeReplacedByOwnership(){doThrow(new BiAccessException(403,"denied")).when(caps).require("bi:asset:add");assertThrows(BiAccessException.class,()->service.upload(new AssetService.Upload("logo","")));verifyNoInteractions(storage,resources);}
 @Test void searchOnlyAuthorizedNames(){when(storage.list()).thenReturn(List.of(Map.of("id",1L,"name","Hidden Logo"),Map.of("id",2L,"name","Logo")));when(resources.allowed("asset",2L,"view")).thenReturn(true);assertEquals(1,service.list(1,"LOGO").get("total"));assertEquals(0,service.list(1,"Hidden").get("total"));}
 @Test void staleRenameRejected(){when(storage.get(7L)).thenReturn(Map.of("name","old"));when(storage.rename(7L,"new","old")).thenReturn(false);assertEquals(409,assertThrows(BiAccessException.class,()->service.rename(7L,new AssetService.Rename("new","old"))).getCode());}
 @Test void renameChecksResourceBeforeStorage(){doThrow(new BiAccessException(403,"denied")).when(resources).require("asset",7L,"edit");assertThrows(BiAccessException.class,()->service.rename(7L,new AssetService.Rename("new","old")));verifyNoInteractions(storage);}
 @Test void grantsRequireActiveResource(){when(storage.get(7L)).thenReturn(null);assertThrows(IllegalArgumentException.class,()->service.grants(7L));verify(resources,never()).grants(anyString(),anyLong());}
 @Test void grantUsesResourceAuthorizer(){when(storage.get(7L)).thenReturn(Map.of("name","logo"));service.grant(7L,9L,"view",true);verify(resources).change("asset",7L,9L,"view",true);}
}
