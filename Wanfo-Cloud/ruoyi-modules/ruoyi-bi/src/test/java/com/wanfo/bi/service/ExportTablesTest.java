package com.wanfo.bi.service;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ExportTablesTest {
 static ExportTables.Sheet sheet(Object formula){return new ExportTables.Sheet("经营",List.of("name","value"),List.of(Map.of("name",formula,"value",2)));}
 @Test void csvUsesBomAndQuotesFormulas(){
  byte[] bytes=ExportTables.csv(sheet("=1+1"));
  String text=new String(bytes,StandardCharsets.UTF_8);
  assertTrue(text.startsWith("\uFEFF"));
  assertTrue(text.contains("'=1+1"));
  assertFalse(text.contains("\n=1+1"));
 }
 @Test void csvPrefixesAtPlusMinusAndFullwidth(){
  for(String raw:List.of("@SUM(A1)","+2+3","-2+3","＝1+1"))assertTrue(new String(ExportTables.csv(sheet(raw)),StandardCharsets.UTF_8).contains("'"+raw));
 }
 @Test void numbersAreNotPrefixed(){
  String text=new String(ExportTables.csv(new ExportTables.Sheet("t",List.of("n"),List.of(Map.of("n",-12)))),StandardCharsets.UTF_8);
  assertTrue(text.contains("-12"));
  assertFalse(text.contains("'-12"));
 }
 @Test void xlsxIsZipAndEscapesInlineText()throws Exception{
  byte[] bytes=ExportTables.xlsx(List.of(sheet("=cmd|' /c calc'!A0")));
  assertEquals('P',bytes[0]);assertEquals('K',bytes[1]);
  try(var workbook=org.apache.poi.ss.usermodel.WorkbookFactory.create(new ByteArrayInputStream(bytes))){
   assertEquals("'=cmd|' /c calc'!A0",workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
  }
 }
 @Test void pdfStartsWithHeader(){byte[] bytes=ExportTables.pdf(sheet("标题"));assertTrue(bytes.length>4);assertEquals('%',bytes[0]);assertEquals('P',bytes[1]);}
 @Test void fileNameStripsPathCharacters(){assertEquals("经营_看板.xlsx",ExportTables.fileName("经营/看板","xlsx"));assertEquals("经营_看板.pdf",ExportTables.fileName("经营/看板","pdf"));}
 @Test void columnNameCoversDoubleLetters(){assertEquals("A",ExportTables.col(0));assertEquals("Z",ExportTables.col(25));assertEquals("AA",ExportTables.col(26));}
}
