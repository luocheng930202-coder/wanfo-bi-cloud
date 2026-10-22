package com.wanfo.bi.service;

import java.util.*;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.*;
import com.wanfo.bi.domain.query.QueryModel;
import com.wanfo.bi.domain.chart.*;
import static org.junit.jupiter.api.Assertions.*;

class PivotExportTest {
    static ChartDefinitionService.Definition definition(boolean inColumns,boolean first){
        var q=new QueryModel();q.addDimension("province","r1");q.addDimension("city","r2");q.addDimension("channel","c1");q.addDimension("quarter","c2");q.addMetric("amount","AVG","mean");q.addMetric("customer","COUNT_DISTINCT","buyers");
        var a=new ChartAdvancedOptions();a.setPivotRowFields(List.of("province","city"));a.setPivotColFields(List.of("channel","quarter"));
        var s=new ChartVisualStyle();s.setPivotRowTotals(true);s.setPivotColTotals(true);s.setPivotRowSubtotals(true);s.setPivotColSubtotals(true);s.setPivotValuesInCols(inColumns);s.setPivotTotalsFirst(first);
        return new ChartDefinitionService.Definition(1,"经营透视","pivot",q,s,a);
    }
    static Map<String,Object> data(){
        List<List<Object>> source=List.of(List.of("A","x","web","Q1",10,"same"),List.of("A","x","web","Q1",20,"a"),Arrays.asList("A",null,"web","Q2",100,"same"),List.of("B","z","shop","Q1",50,"b"));
        var cells=new ArrayList<Map<String,Object>>();var detail=new ArrayList<Map<String,Object>>();var fields=List.of("r1","r2","c1","c2");
        for(int rd=0;rd<=2;rd++)for(int cd=0;cd<=2;cd++){
            Map<List<Object>,List<List<Object>>> groups=new LinkedHashMap<>();
            for(var fact:source){var key=new ArrayList<Object>();key.addAll(fact.subList(0,rd));key.addAll(fact.subList(2,2+cd));groups.computeIfAbsent(key,k->new ArrayList<>()).add(fact);}
            for(var entry:groups.entrySet()){
                var dims=new LinkedHashMap<String,Object>();int j=0;for(int i=0;i<rd;i++)dims.put(fields.get(i),entry.getKey().get(j++));for(int i=0;i<cd;i++)dims.put(fields.get(2+i),entry.getKey().get(j++));
                var facts=entry.getValue();var mean=BigDecimal.valueOf(facts.stream().mapToInt(f->(Integer)f.get(4)).sum()).divide(BigDecimal.valueOf(facts.size()),12,java.math.RoundingMode.HALF_UP).toPlainString();
                Map<String,Object> values=Map.of("mean",mean,"buyers",String.valueOf(facts.stream().map(f->f.get(5)).distinct().count()));
                if(rd==2 && cd==2){var row=new LinkedHashMap<>(dims);row.putAll(values);detail.add(row);}else cells.add(new LinkedHashMap<>(Map.of("rowDepth",rd,"colDepth",cd,"dimensions",dims,"values",values)));
            }
        }
        return new LinkedHashMap<>(Map.of("columns",List.of("r1","r2","c1","c2","mean","buyers"),"rows",detail,"pivotTotals",new LinkedHashMap<>(Map.of("status","complete","scope","filtered","rowFields",List.of("r1","r2"),"colFields",List.of("c1","c2"),"metricKeys",List.of("mean","buyers"),"detailTruncated",false,"cells",cells))));
    }
    @Test void xlsxContainsNativeServerTotalsMergedHeadersAndNullGroups()throws Exception{
        var sheet=PivotExport.sheet("透视",definition(true,false),data(),Map.of("mean","平均金额","buyers","去重客户"));
        byte[] bytes=ExportTables.xlsx(List.of(sheet));
        try(var book=WorkbookFactory.create(new ByteArrayInputStream(bytes))){
            var tab=book.getSheetAt(0);assertTrue(tab.getNumMergedRegions()>3);assertEquals(4,tab.getPaneInformation().getHorizontalSplitPosition());assertEquals(2,tab.getPaneInformation().getVerticalSplitPosition());
            int last=tab.getLastRowNum(),right=tab.getRow(last).getLastCellNum();
            assertEquals("筛选范围总计",tab.getRow(last).getCell(0).getStringCellValue());assertEquals(45,tab.getRow(last).getCell(right-2).getNumericCellValue());assertEquals(3,tab.getRow(last).getCell(right-1).getNumericCellValue());
            assertTrue(sheet.grid().cells().stream().anyMatch(row->row.contains("（空值）")));assertEquals("平均金额",tab.getRow(3).getCell(2).getStringCellValue());
            assertTrue(sheet.grid().summaryRows().size()>1);assertTrue(sheet.grid().summaryColumns().size()>2);
            for(var row:tab)for(var cell:row)assertNotEquals(CellType.FORMULA,cell.getCellType());
        }
    }
    @Test void valuesInRowsAndTotalsFirstUseTheSameExactNumbers()throws Exception{
        var sheet=PivotExport.sheet("透视",definition(false,true),data(),Map.of("mean","平均金额","buyers","去重客户"));
        try(var book=WorkbookFactory.create(new ByteArrayInputStream(ExportTables.xlsx(List.of(sheet))))){
            var tab=book.getSheetAt(0);assertEquals("筛选范围总计",tab.getRow(3).getCell(0).getStringCellValue());assertEquals("平均金额",tab.getRow(3).getCell(2).getStringCellValue());assertEquals(45,tab.getRow(3).getCell(3).getNumericCellValue());assertEquals(3,tab.getRow(4).getCell(3).getNumericCellValue());
        }
    }
    @Test @SuppressWarnings("unchecked") void truncationIsDisclosedAndHighPrecisionIsKeptAsText()throws Exception{
        var data=data();var totals=(Map<String,Object>)data.get("pivotTotals");totals.put("detailTruncated",true);
        var cells=(List<Map<String,Object>>)totals.get("cells");var grand=cells.stream().filter(c->c.get("rowDepth").equals(0)&&c.get("colDepth").equals(0)).findFirst().orElseThrow();
        for(String exact:List.of("123456789012345678.123456","1E-400")){
            grand.put("values",Map.of("mean",exact,"buyers","3"));var sheet=PivotExport.sheet("透视",definition(true,false),data,Map.of());
            try(var book=WorkbookFactory.create(new ByteArrayInputStream(ExportTables.xlsx(List.of(sheet))))){var tab=book.getSheetAt(0);assertTrue(tab.getRow(0).getCell(0).getStringCellValue().contains("明细已截断"));var row=tab.getRow(tab.getLastRowNum());assertEquals(exact,row.getCell(row.getLastCellNum()-2).getStringCellValue());}
        }
    }
    @Test @SuppressWarnings("unchecked") void unavailableOrMismatchedTotalsNeverBecomePartialExport(){
        var data=data();var totals=(Map<String,Object>)data.get("pivotTotals");totals.put("status","unavailable");totals.put("reason","汇总分组超限");assertTrue(assertThrows(IllegalArgumentException.class,()->PivotExport.sheet("t",definition(true,false),data,Map.of())).getMessage().contains("超限"));
        totals.put("status","complete");totals.put("rowFields",List.of("wrong"));assertThrows(IllegalArgumentException.class,()->PivotExport.sheet("t",definition(true,false),data,Map.of()));
    }
    @Test @SuppressWarnings("unchecked") void dangerousDimensionTextIsNeverAnExcelFormulaAndEmptyResultIsValid()throws Exception{
        var data=data();var rows=(List<Map<String,Object>>)data.get("rows");rows.forEach(row->row.put("r2","=cmd()"));
        var d=definition(true,false);d.style().setPivotRowTotals(false);d.style().setPivotColTotals(false);d.style().setPivotRowSubtotals(false);d.style().setPivotColSubtotals(false);data.remove("pivotTotals");
        var sheet=PivotExport.sheet("t",d,data,Map.of());try(var book=WorkbookFactory.create(new ByteArrayInputStream(ExportTables.xlsx(List.of(sheet))))){assertEquals("'=cmd()",book.getSheetAt(0).getRow(4).getCell(1).getStringCellValue());}
        data.put("rows",List.of());assertEquals(0,PivotExport.sheet("t",d,data,Map.of()).rowCount());
    }
    @Test void widePivotFailsBeforeCreatingWorkbook(){
        var data=data();var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<130;i++)rows.add(Map.of("r1","A","r2","x","c1","channel"+i,"c2","Q1","mean","1","buyers","1"));data.put("rows",rows);
        assertThrows(IllegalArgumentException.class,()->PivotExport.sheet("t",definition(true,false),data,Map.of()));
    }
}
