package com.wanfo.bi.datasource;

import com.wanfo.bi.datasource.snapshot.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotParserTest {
    private byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    @Test void csvSupportsBomUnicodeQuotesMultilineAndPreservesIdentifiers()throws Exception {
        var data=SnapshotParser.csv(bytes("\uFEFFcode,name,amount,date\r\n001,\"北,京\",12.25,2026-10-23\r\n002,\"a\"\"b\nc\",2.50,2026-10-24\r\n"));
        assertEquals("VARCHAR",data.columns().get(0).type());assertEquals("001",data.rows().get(0).get(0));assertEquals("北,京",data.rows().get(0).get(1));
        assertEquals("DECIMAL",data.columns().get(2).type());assertEquals("DATE",data.columns().get(3).type());assertEquals(2,data.rows().size());
    }
    @Test void csvRejectsMalformedOrOversizedInput(){
        for(String input:List.of("a,a\n1,2","a,b\n1","a\n\"open","a\n\"closed\"junk","a\nx"+"x".repeat(2000)))assertThrows(IllegalArgumentException.class,()->SnapshotParser.csv(bytes(input)));
        assertThrows(java.nio.charset.CharacterCodingException.class,()->SnapshotParser.csv(new byte[]{(byte)0xff}));
    }
    @Test void jsonIsFlatBoundedAndDoesNotSilentlyImportPartialPages()throws Exception {
        var mapper=new ObjectMapper();var data=SnapshotParser.json(bytes("{\"data\":{\"rows\":[{\"name\":\"A\",\"amount\":12},{\"name\":null,\"amount\":null}]}}"),"data.rows",mapper);
        assertEquals(2,data.rows().size());assertNull(data.rows().get(1).get(0));
        for(String input:List.of("[{\"a\":{\"b\":1}}]","[{\"a\":1},{\"b\":1}]","{\"total\":10,\"rows\":[{\"a\":1}]}"))assertThrows(IllegalArgumentException.class,()->SnapshotParser.json(bytes(input),input.startsWith("{")?"rows":"",mapper));
    }
    @Test void failedRefreshRejectsChangedFieldsAndTypes()throws Exception {
        var old=SnapshotParser.csv(bytes("code,amount\n001,12.3"));
        var next=SnapshotParser.csv(bytes("code,amount\n002,13.3"));assertEquals(old.columns(),next.compatibleWith(old).columns());
        assertThrows(IllegalArgumentException.class,()->SnapshotParser.csv(bytes("code,amount\n003,oops")).compatibleWith(old));
        assertThrows(IllegalArgumentException.class,()->SnapshotParser.csv(bytes("amount,code\n1,003")).compatibleWith(old));
        assertThrows(UnsupportedOperationException.class,()->old.rows().get(0).set(0,"bad"));
    }
    @Test void excelReadsSelectedSheetAndRejectsFormulas()throws Exception {
        try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            workbook.createSheet("ignore");var sheet=workbook.createSheet("sales");var header=sheet.createRow(0);header.createCell(0).setCellValue("region");header.createCell(1).setCellValue("amount");
            var row=sheet.createRow(1);row.createCell(0).setCellValue("北京");row.createCell(1).setCellValue(12.5);workbook.write(out);
            var data=SnapshotParser.file(out.toByteArray(),"test.xlsx","sales");assertEquals("北京",data.rows().get(0).get(0));assertEquals("DECIMAL",data.columns().get(1).type());
            row.getCell(1).setCellFormula("1+1");out.reset();workbook.write(out);assertThrows(IllegalArgumentException.class,()->SnapshotParser.file(out.toByteArray(),"test.xlsx","sales"));
        }
    }
}
