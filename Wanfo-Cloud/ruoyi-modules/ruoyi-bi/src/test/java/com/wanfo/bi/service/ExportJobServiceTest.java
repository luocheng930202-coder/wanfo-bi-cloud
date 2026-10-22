package com.wanfo.bi.service;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.wanfo.bi.mapper.BiExportJobMapper;
import com.wanfo.bi.security.*;
import tools.jackson.databind.ObjectMapper;
class ExportJobServiceTest {
 BiExportJobMapper mapper=mock(BiExportJobMapper.class);ChartDefinitionService charts=mock(ChartDefinitionService.class);DashboardService dashboards=mock(DashboardService.class);
 CapabilityProvider caps=mock(CapabilityProvider.class);CurrentActorProvider actors=()->new BiActor("test",1L,"alice",null,Set.of());
 ExportJobService service=new ExportJobService(mapper,charts,dashboards,caps,actors,new ObjectMapper(),Runnable::run, ref->new LiveActorResolver.Snapshot(ref,Set.of("*:*:*")));
 @Test void chartSubmitQueuesAndWritesCsvWithoutQueryModel()throws Exception{
  when(charts.detail(9L)).thenReturn(Map.of("id",9L,"definition",new ChartDefinitionService.Definition(1,"城市",null,null)));
  when(charts.data(9L)).thenReturn(Map.of("columns",List.of("name","value"),"rows",List.of(Map.of("name","=1+1","value",3)),"executedSql","SELECT 1"));
  AtomicReference<Map<String,Object>> inserted=new AtomicReference<>();
  doAnswer(inv->{Map<String,Object> row=inv.getArgument(0);row.put("id",4L);inserted.set(new LinkedHashMap<>(row));return 1;}).when(mapper).insert(any());
  when(mapper.get(4L)).thenAnswer(inv->Map.of("id",4L,"name","城市","requestJson",inserted.get().get("requestJson"),"format","csv"));
  when(mapper.claim(4L)).thenReturn(1);
  AtomicReference<Map<String,Object>> finished=new AtomicReference<>();
  doAnswer(inv->{finished.set(inv.getArgument(0));return 1;}).when(mapper).finish(any());
  var created=service.submit(new ExportJobService.Submit("chart",9L,null,null,"csv",null,null));
  assertEquals(4L,created.get("id"));assertEquals("queued",created.get("status"));
  assertFalse(String.valueOf(inserted.get().get("requestJson")).contains("datasetId"));
  assertEquals("succeeded",finished.get().get("status"));
  String csv=new String((byte[])finished.get().get("content"),java.nio.charset.StandardCharsets.UTF_8);
  assertTrue(csv.startsWith("\uFEFF"));assertTrue(csv.contains("'=1+1"));assertFalse(csv.contains("SELECT 1"));
  verify(charts).data(9L);
 }
 @Test void queryModelAndSqlInputRejected(){
  assertThrows(IllegalArgumentException.class,()->service.submit(new ExportJobService.Submit("chart",9L,null,null,"csv","n",Map.of("datasetId",1))));
  assertThrows(IllegalArgumentException.class,()->service.submit(new ExportJobService.Submit("dashboard",3L,null,1,"xlsx","n",Map.of("sql","select 1"))));
  verifyNoInteractions(mapper);
 }
 @Test void csvRequiresSingleDashboardPanel(){
  when(dashboards.view(3L)).thenReturn(Map.of("name","经营","revision",2,"panels",List.of(Map.of("key","a","title","A","componentType","chart"),Map.of("key","b","title","B","componentType","kpi"))));
  assertThrows(IllegalArgumentException.class,()->service.submit(new ExportJobService.Submit("dashboard",3L,null,2,"csv",null,null)));
  verify(mapper,never()).insert(any());
 }
 @Test void functionPermissionRequiredToSubmit(){
  doThrow(new BiAccessException(403,"denied")).when(caps).require("bi:export:add");
  assertThrows(BiAccessException.class,()->service.submit(new ExportJobService.Submit("chart",9L,null,null,"csv","n",null)));
  verifyNoInteractions(mapper,charts,dashboards);
 }
 @Test void downloadRequiresOwnerAndSucceededFile(){
  when(mapper.getFile(4L)).thenReturn(Map.of("id",4L,"status","succeeded","fileName","a.csv","contentType","text/csv","content","ok".getBytes(),"ownerPrincipal",2L,"expireTime",new Date(System.currentTimeMillis()+86_400_000)));
  assertEquals(403,assertThrows(BiAccessException.class,()->service.download(4L)).getCode());
 }
 @Test void otherUserCannotCancel(){
  when(mapper.get(4L)).thenReturn(Map.of("id",4L,"ownerPrincipal",99L,"status","queued"));
  assertEquals(403,assertThrows(BiAccessException.class,()->service.cancel(4L)).getCode());
  verify(mapper,never()).cancel(anyLong(),anyLong());
 }
 @Test void listOmitsBlobAndRequest(){
  when(mapper.list(1L)).thenReturn(List.of(Map.of("id",4L,"name","城市","status","succeeded","fileName","城市.csv","requestJson","secret","content","x".getBytes())));
  var items=service.list();
  assertEquals(1,items.size());
  assertFalse(items.get(0).containsKey("requestJson"));
  assertFalse(items.get(0).containsKey("content"));
 }
 AtomicReference<Map<String,Object>> captureXlsxJob() {
  AtomicReference<Map<String,Object>> inserted=new AtomicReference<>(),finished=new AtomicReference<>();
  doAnswer(inv->{Map<String,Object> row=inv.getArgument(0);row.put("id",4L);inserted.set(new LinkedHashMap<>(row));return 1;}).when(mapper).insert(any());
  when(mapper.get(4L)).thenAnswer(inv->Map.of("id",4L,"name","透视","requestJson",inserted.get().get("requestJson"),"format","xlsx"));when(mapper.claim(4L)).thenReturn(1);
  doAnswer(inv->{finished.set(inv.getArgument(0));return 1;}).when(mapper).finish(any());return finished;
 }
 @Test @SuppressWarnings("unchecked") void pivotSummaryChangesInvalidatePreviouslyExportedWorkbook()throws Exception {
  var d=PivotExportTest.definition(true,false);var data=PivotExportTest.data();
  when(charts.detail(9L)).thenReturn(Map.of("definition",d));when(charts.exportData(9L)).thenReturn(Map.of("definition",d,"data",data));
  var finished=captureXlsxJob();service.submit(new ExportJobService.Submit("chart",9L,null,null,"xlsx",null,null));
  assertEquals("succeeded",finished.get().get("status"));assertTrue(((Number)finished.get().get("rowCount")).intValue()>3);
  Map<String,Object> file=new LinkedHashMap<>(finished.get());file.put("name","透视");file.put("requestJson",file.get("validationJson"));file.put("ownerPrincipal",1L);file.put("expireTime",new Date(System.currentTimeMillis()+86400000));when(mapper.getFile(4L)).thenReturn(file);
  assertTrue(service.download(4L).bytes().length>0);
  var totals=(Map<String,Object>)data.get("pivotTotals");var cells=(List<Map<String,Object>>)totals.get("cells");cells.get(0).put("values",Map.of("mean","999","buyers","3"));
  assertEquals(409,assertThrows(BiAccessException.class,()->service.download(4L)).getCode());
  verify(charts,never()).data(anyLong());
 }
 @Test void dashboardPivotExportUsesPublishedPresentationAndRuntimeFilters()throws Exception {
  var d=PivotExportTest.definition(false,true);var input=Map.<String,Object>of("filters",Map.of("region","A"));
  when(dashboards.view(3L)).thenReturn(Map.of("name","发布看板","revision",2,"panels",List.of(Map.of("key","pivot","title","发布透视","componentType","chart"))));
  Map<String,Object> presentation=Map.of("type","pivot","style",d.style(),"advanced",d.advanced(),"queryDimensions",d.query().getDimensions(),"queryMetrics",d.query().getMetrics(),"fieldLabels",Map.of("mean","发布均值"));
  when(dashboards.panelData(3L,"pivot",2,input)).thenReturn(Map.of("data",PivotExportTest.data(),"presentation",presentation));
  var finished=captureXlsxJob();service.submit(new ExportJobService.Submit("dashboard",3L,"pivot",2,"xlsx",null,input));assertEquals("succeeded",finished.get().get("status"));
  try(var book=org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream((byte[])finished.get().get("content")))){var tab=book.getSheetAt(0);assertEquals("发布均值",tab.getRow(3).getCell(2).getStringCellValue());assertEquals(45,tab.getRow(3).getCell(3).getNumericCellValue());}
  verifyNoInteractions(charts);verify(dashboards).panelData(3L,"pivot",2,input);
 }
}
