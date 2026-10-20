package com.wanfo.bi.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.wanfo.bi.domain.BiDashboardDO;

class DashboardContainersTest {
    DashboardService.Panel panel(String key,String type,String parent,int x,int y,int w,int h,boolean hidden,boolean locked){
        var content=type.equals("container")?new DashboardContent.Content(null,null,null,DashboardContent.container(null)):new DashboardContent.Content("Text",null);
        return new DashboardService.Panel(key,null,key,x,y,w,h,type,null,content,locked,null,null,hidden,parent);
    }
    DashboardService.Panel box(){return panel("box","container",null,20,20,300,240,false,false);}
    DashboardService.Panel child(){return panel("child","text","box",40,40,100,100,false,false);}
    DashboardService.Definition doc(List<DashboardService.Panel> panels){return new DashboardService.Definition(1,"Container",panels,"screen","absolute",null,null,new DashboardPresentation.Scene(640,480,"contain"));}
    @Test void saveRetainsParentAndStyle(){var f=new DashboardTest();doAnswer(c->{((BiDashboardDO)c.getArgument(0)).setId(8L);return 1;}).when(f.mapper).insert(any());f.service.create(new DashboardService.SaveRequest(null,doc(List.of(box(),child()))));var capture=org.mockito.ArgumentCaptor.forClass(BiDashboardDO.class);verify(f.mapper).insert(capture.capture());var d=f.json.readValue(capture.getValue().getDefinitionJson(),DashboardService.Definition.class);assertEquals("box",d.panels().get(1).parentKey());assertEquals(12,d.panels().get(0).content().container().padding());}
    @Test void invalidGraphsAndOverflowRejectBeforePersist(){var f=new DashboardTest();var orphan=panel("c","text","missing",40,40,100,100,false,false);var nested=panel("inner","container","box",40,40,100,100,false,false);var overflow=panel("c","text","box",21,40,100,100,false,false);var other=panel("other","text",null,500,20,100,100,false,false);for(var panels:List.of(List.of(box(),orphan),List.of(box(),nested),List.of(child(),box()),List.of(box(),other,child()),List.of(box(),overflow),List.of(panel("box","container","box",20,20,300,240,false,false))))assertThrows(IllegalArgumentException.class,()->f.service.create(new DashboardService.SaveRequest(null,doc(panels))));verify(f.mapper,never()).insert(any());verifyNoInteractions(f.queries);}
    @Test void inheritanceCannotBeBypassed(){var f=new DashboardTest();for(var p:List.of(panel("box","container",null,20,20,300,240,true,false),panel("box","container",null,20,20,300,240,false,true)))assertThrows(IllegalArgumentException.class,()->f.service.create(new DashboardService.SaveRequest(null,doc(List.of(p,child())))));}
    @Test void publicationKeepsStructureAndConsumerManifest(){var f=new DashboardTest();var row=f.stored(false);row.setDefinitionJson(f.json.writeValueAsString(doc(List.of(box(),child()))));when(f.mapper.publish(eq(7L),eq(2),eq(3),anyString(),eq("author"))).thenReturn(1);f.service.publish(7L,2);var capture=org.mockito.ArgumentCaptor.forClass(String.class);verify(f.mapper).publish(eq(7L),eq(2),eq(3),capture.capture(),eq("author"));row.setPublishedJson(capture.getValue());row.setPublishedVersion(3);assertTrue(f.json.writeValueAsString(f.service.view(7L)).contains("parentKey\":\"box"));assertEquals(12,f.json.readValue(capture.getValue(),DashboardService.Publication.class).panels().get(0).layout().content().container().padding());}
    @Test void staticContainerPreviewNeverQueriesAndStyleIsBounded() throws Exception {var f=new DashboardTest();assertEquals(Map.of(),f.service.preview(null,new DashboardService.PreviewRequest(null,doc(List.of(box(),child())),Map.of())).get("payloads"));verifyNoInteractions(f.queries);for(var style:List.of(new DashboardContent.ContainerStyle("url(x)","#123456",1,8,12),new DashboardContent.ContainerStyle("#123456","#123456",13,8,12),new DashboardContent.ContainerStyle("#123456","#123456",1,81,12),new DashboardContent.ContainerStyle("#123456","#123456",1,8,41)))assertThrows(IllegalArgumentException.class,()->DashboardContent.container(style));}
}
