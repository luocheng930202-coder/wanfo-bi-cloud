package com.wanfo.bi.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.wanfo.bi.mapper.BiScreenTemplateMapper;
import com.wanfo.bi.security.*;
import tools.jackson.databind.ObjectMapper;
class ScreenTemplateServiceTest {
 BiScreenTemplateMapper mapper=mock(BiScreenTemplateMapper.class);BiResourceAuthorizer resources=mock(BiResourceAuthorizer.class);CapabilityProvider caps=mock(CapabilityProvider.class);
 ScreenTemplateService service=new ScreenTemplateService(mapper,resources,caps,()->new BiActor("test",1L,"alice",null,Set.of()),new ObjectMapper());
 static Map<String,Object> builtin(String name){try(var input=ScreenTemplateServiceTest.class.getResourceAsStream("/bi/template-market/"+name+".json")){return new ObjectMapper().readValue(input,Map.class);}catch(Exception ex){throw new IllegalStateException(ex);}}
 static Map<String,Object> layout(){return builtin("market-screen-ops");}
 @Test void createRegistersOwnerAfterValidation(){doAnswer(inv->{((Map<String,Object>)inv.getArgument(0)).put("id",8L);return 1;}).when(mapper).insert(any());var id=service.create(new ScreenTemplateService.SaveRequest(" 经营 ","经营分析","说明",layout())).get("id");assertEquals(8L,id);verify(resources).register("template",8L);}
 static final String PIXEL="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1ZkAAAAASUVORK5CYII=";
 static Map<String,Object> imagePanel(String image){var p=new HashMap<String,Object>();p.put("key","logo");p.put("title","标志");p.put("componentType","image");p.put("content",Map.of("image",image));p.put("x",0);p.put("y",0);p.put("w",100);p.put("h",100);p.put("style",Map.of("showTitle",true,"titleAlign","left"));p.put("locked",false);p.put("hidden",false);return p;}
 @Test void bindingsRejected(){var bad=new HashMap<>(layout());bad.put("chartId",9);assertThrows(IllegalArgumentException.class,()->service.create(new ScreenTemplateService.SaveRequest("n","c","",bad)));verifyNoInteractions(mapper,resources);}
 @Test void packedPngAcceptedOnScreenAndDashboard(){
  var screen=new HashMap<>(layout());screen.put("panels",List.of(imagePanel(PIXEL)));
  assertEquals("datacenter-screen-template",ScreenTemplateService.validate(screen).get("format"));
  var grid=builtin("market-dashboard-retail");var image=imagePanel(PIXEL);image.remove("hidden");image.put("w",4);image.put("h",4);grid.put("panels",List.of(image));assertEquals("datacenter-dashboard-template",ScreenTemplateService.validate(grid).get("format"));
 }
 @Test void remoteEmptySvgAndNonImagePayloadsRejected(){
  var remote=new HashMap<>(layout());remote.put("panels",List.of(imagePanel("https://x/a.png")));
  var empty=new HashMap<>(layout());empty.put("panels",List.of(imagePanel("")));
  var svg=new HashMap<>(layout());svg.put("panels",List.of(imagePanel("data:image/svg+xml;base64,PHN2Zz4=")));
  var leaked=new HashMap<>(layout());leaked.put("panels",List.of(Map.of("componentType","text","content",Map.of("text","data:image/png;base64,xx"))));
  var many=new HashMap<>(layout());many.put("panels",java.util.stream.IntStream.range(0,6).mapToObj(i->imagePanel(PIXEL)).toList());
  for(var bad:List.of(remote,empty,svg,leaked,many))assertThrows(IllegalArgumentException.class,()->ScreenTemplateService.validate(bad));
 }
 @Test void unknownFormatRejected(){assertThrows(IllegalArgumentException.class,()->ScreenTemplateService.validate(Map.of("format","det2","version",1)));}
 @Test void dashboardFormatAccepted(){assertEquals("datacenter-dashboard-template",ScreenTemplateService.validate(builtin("market-dashboard-retail")).get("format"));}
 @Test void emptyPanelsRejected(){var bad=layout();bad.put("panels",List.of());assertThrows(IllegalArgumentException.class,()->ScreenTemplateService.validate(bad));}
 @Test void listHidesUnauthorized(){when(mapper.list()).thenReturn(List.of(Map.of("id",1L,"name","a","contentJson","{\"format\":\"datacenter-screen-template\",\"version\":1}"),Map.of("id",2L,"name","b","contentJson","{\"format\":\"datacenter-screen-template\",\"version\":1}")));when(resources.allowed("template",2L,"view")).thenReturn(true);var result=service.list();assertEquals(1,result.size());assertEquals(2L,((Number)result.get(0).get("id")).longValue());assertFalse(result.get(0).containsKey("contentJson"));}
 @Test void summariesOmitContent(){when(mapper.list()).thenReturn(List.of(Map.of("id",3L,"name","n","category","c","description","d","contentJson","{\"format\":\"datacenter-dashboard-template\"}")));when(resources.allowed("template",3L,"view")).thenReturn(true);var row=service.listSummaries().get(0);assertEquals("dashboard",row.get("kind"));assertFalse(row.containsKey("content"));}
 @Test void functionPermissionRequired(){doThrow(new BiAccessException(403,"denied")).when(caps).require("bi:template:add");assertThrows(BiAccessException.class,()->service.create(new ScreenTemplateService.SaveRequest("n","c","",layout())));verifyNoInteractions(mapper,resources);}
}
