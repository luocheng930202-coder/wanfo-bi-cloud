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
    @Test void jsonNumbersPreserveExactPrecisionAndBoundScientificNotation()throws Exception {
        var mapper=new ObjectMapper();var data=SnapshotParser.json(bytes("[{\"amount\":1234567890123456.123456789012,\"id\":123456789012345678901234567890,\"exponent\":1.25e3}]"),"",mapper);
        assertEquals("1234567890123456.123456789012",data.rows().get(0).get(0));assertEquals("DECIMAL",data.columns().get(0).type());
        assertEquals("123456789012345678901234567890",data.rows().get(0).get(1));assertEquals("VARCHAR",data.columns().get(1).type());
        assertEquals("1250",data.rows().get(0).get(2));assertEquals("DECIMAL",data.columns().get(2).type());
        assertThrows(IllegalArgumentException.class,()->SnapshotParser.json(bytes("[{\"a\":1e10000}]"),"",mapper));
    }
    @Test void expandsNestedObjectArraysIntoRows()throws Exception {
        var mapper=new ObjectMapper();
        var data=SnapshotParser.json(bytes("{\"data\":{\"rows\":[{\"orderId\":7,\"region\":\"北京\",\"items\":[{\"sku\":\"A\",\"qty\":2},{\"sku\":\"B\",\"qty\":3}]},{\"orderId\":8,\"region\":\"上海\",\"items\":[{\"sku\":\"C\",\"qty\":1}]}]}}"),"data.rows",mapper,false,true,"items");
        assertEquals(List.of("orderId","region","items.sku","items.qty"),data.columns().stream().map(SnapshotData.Column::name).toList());
        assertEquals(List.of(List.of("7","北京","A","2"),List.of("7","北京","B","3"),List.of("8","上海","C","1")),data.rows());
    }
    @Test void expandsMultipleSiblingArraysUsingBoundedCartesianAssociation()throws Exception {
        var mapper=new ObjectMapper();String input="[{\"orderId\":9,\"items\":[{\"sku\":\"A\"},{\"sku\":\"B\"}],\"payments\":[{\"method\":\"card\"},{\"method\":\"cash\"}]}]";
        var data=SnapshotParser.json(bytes(input),"",mapper,false,true,List.of("items","payments"));
        assertEquals(List.of("orderId","items.sku","payments.method"),data.columns().stream().map(SnapshotData.Column::name).toList());
        assertEquals(List.of(List.of("9","A","card"),List.of("9","A","cash"),List.of("9","B","card"),List.of("9","B","cash")),data.rows());
        assertThrows(IllegalArgumentException.class,()->SnapshotParser.json(bytes(input),"",mapper,false,true,List.of("items","items.sku")));
    }
    @Test void rejectsInconsistentNestedArrayRowsAndBoundsExpansion(){
        var mapper=new ObjectMapper();
        assertThrows(IllegalArgumentException.class,()->SnapshotParser.json(bytes("[{\"id\":1,\"items\":[{\"sku\":\"A\"}]},{\"id\":2,\"items\":[{\"code\":\"B\"}]}]"),"",mapper,false,true,"items"));
        StringBuilder input=new StringBuilder("[{\"id\":1,\"items\":[");
        for(int i=0;i<=SnapshotData.MAX_ROWS;i++){if(i>0)input.append(',');input.append("{\"sku\":\"A\"}");}input.append("]}]");
        assertThrows(IllegalArgumentException.class,()->SnapshotParser.json(bytes(input.toString()),"",mapper,false,true,"items"));
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
    @Test void parquetReadsFlatPrimitivesAndRejectsEmpty()throws Exception {
        org.apache.avro.Schema schema=org.apache.avro.SchemaBuilder.record("row").fields()
                .requiredString("code").requiredDouble("amount").endRecord();
        ByteArrayOutputStream sink=new ByteArrayOutputStream();
        org.apache.parquet.io.OutputFile out=new org.apache.parquet.io.OutputFile(){
            @Override public org.apache.parquet.io.PositionOutputStream create(long blockSizeHint){return stream();}
            @Override public org.apache.parquet.io.PositionOutputStream createOrOverwrite(long blockSizeHint){sink.reset();return stream();}
            @Override public boolean supportsBlockSize(){return false;}
            @Override public long defaultBlockSize(){return 0;}
            private org.apache.parquet.io.PositionOutputStream stream(){
                return new org.apache.parquet.io.PositionOutputStream(){
                    @Override public long getPos(){return sink.size();}
                    @Override public void write(int b){sink.write(b);}
                    @Override public void write(byte[] b,int off,int len){sink.write(b,off,len);}
                };
            }
        };
        try(org.apache.parquet.hadoop.ParquetWriter<org.apache.avro.generic.GenericRecord> writer=
                    org.apache.parquet.avro.AvroParquetWriter.<org.apache.avro.generic.GenericRecord>builder(out)
                            .withSchema(schema).build()){
            org.apache.avro.generic.GenericRecord r=new org.apache.avro.generic.GenericData.Record(schema);
            r.put("code","001");r.put("amount",12.5);writer.write(r);
            r=new org.apache.avro.generic.GenericData.Record(schema);r.put("code","002");r.put("amount",20.0);writer.write(r);
        }
        byte[] parquet=sink.toByteArray();
        var data=SnapshotParser.parquet(parquet);
        assertEquals(List.of("code","amount"),data.columns().stream().map(SnapshotData.Column::name).toList());
        assertEquals("001",data.rows().get(0).get(0));assertEquals(2,data.rows().size());
        assertEquals(data.columns(),SnapshotParser.file(parquet,"sales.parquet",null).columns());
        assertThrows(IllegalArgumentException.class,()->SnapshotParser.parquet(new byte[0]));
    }

    @Test void parquetFlattensNestedRecordFields() throws Exception {
        org.apache.avro.Schema address = org.apache.avro.SchemaBuilder.record("addr").fields()
                .requiredString("city").requiredInt("zip").endRecord();
        org.apache.avro.Schema schema = org.apache.avro.SchemaBuilder.record("row").fields()
                .requiredString("code")
                .name("address").type(address).noDefault()
                .endRecord();
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        org.apache.parquet.io.OutputFile out = new org.apache.parquet.io.OutputFile() {
            @Override public org.apache.parquet.io.PositionOutputStream create(long blockSizeHint) { return stream(); }
            @Override public org.apache.parquet.io.PositionOutputStream createOrOverwrite(long blockSizeHint) { sink.reset(); return stream(); }
            @Override public boolean supportsBlockSize() { return false; }
            @Override public long defaultBlockSize() { return 0; }
            private org.apache.parquet.io.PositionOutputStream stream() {
                return new org.apache.parquet.io.PositionOutputStream() {
                    @Override public long getPos() { return sink.size(); }
                    @Override public void write(int b) { sink.write(b); }
                    @Override public void write(byte[] b, int off, int len) { sink.write(b, off, len); }
                };
            }
        };
        try (org.apache.parquet.hadoop.ParquetWriter<org.apache.avro.generic.GenericRecord> writer =
                     org.apache.parquet.avro.AvroParquetWriter.<org.apache.avro.generic.GenericRecord>builder(out)
                             .withSchema(schema).build()) {
            org.apache.avro.generic.GenericRecord addr = new org.apache.avro.generic.GenericData.Record(address);
            addr.put("city", "北京");
            addr.put("zip", 100000);
            org.apache.avro.generic.GenericRecord r = new org.apache.avro.generic.GenericData.Record(schema);
            r.put("code", "001");
            r.put("address", addr);
            writer.write(r);
        }
        var data = SnapshotParser.parquet(sink.toByteArray());
        assertEquals(List.of("code", "address.city", "address.zip"), data.columns().stream().map(SnapshotData.Column::name).toList());
        assertEquals("北京", data.rows().get(0).get(1));
        assertEquals("100000", data.rows().get(0).get(2));
    }
}

