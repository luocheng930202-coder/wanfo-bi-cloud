package com.wanfo.bi.datasource.snapshot;

import com.fasterxml.jackson.databind.*;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Strict CSV, scalar JSON objects, and Excel cells. No formulas are evaluated. */
public final class SnapshotParser {
    public static final int MAX_BYTES=5*1024*1024;
    static { org.apache.poi.openxml4j.util.ZipSecureFile.setMaxEntrySize(20L*1024*1024);org.apache.poi.openxml4j.util.ZipSecureFile.setMaxTextSize(10L*1024*1024); }
    private SnapshotParser(){}
    public static SnapshotData file(byte[] bytes,String filename,String sheet)throws IOException {
        if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("文件不能超过 5 MB");
        String lower=filename.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".csv"))return csv(bytes);
        if(!lower.endsWith(".xlsx") && !lower.endsWith(".xls"))throw new IllegalArgumentException("仅支持 CSV、XLS、XLSX");
        try(Workbook workbook=WorkbookFactory.create(new ByteArrayInputStream(bytes))){
            Sheet selected=sheet==null || sheet.isBlank()?workbook.getSheetAt(0):workbook.getSheet(sheet);
            if(selected==null)throw new IllegalArgumentException("工作表不存在");
            if(selected.getLastRowNum()>SnapshotData.MAX_ROWS)throw new IllegalArgumentException("文件超过 20000 行");
            List<List<String>> rows=new ArrayList<>();
            int width=-1;
            for(Row row:selected){
                if(width<0){width=row.getLastCellNum();if(width<1 || width>SnapshotData.MAX_COLUMNS)throw new IllegalArgumentException("表头须为 1～200 列");}
                if(row.getLastCellNum()>width)throw new IllegalArgumentException("数据行超出表头列数");
                List<String> values=new ArrayList<>();
                for(int i=0;i<width;i++){
                    Cell cell=row.getCell(i,Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value=null;
                    if(cell!=null)value=switch(cell.getCellType()){
                        case FORMULA,ERROR->throw new IllegalArgumentException("请将公式或错误单元格转换为值后导入");
                        case BOOLEAN->String.valueOf(cell.getBooleanCellValue());
                        case NUMERIC->DateUtil.isCellDateFormatted(cell)?cell.getLocalDateTimeCellValue().toString():org.apache.poi.ss.util.NumberToTextConverter.toText(cell.getNumericCellValue());
                        case STRING->cell.getStringCellValue();
                        default->null;
                    };
                    if(value!=null && value.length()>SnapshotData.MAX_CELL)throw new IllegalArgumentException("单元格超过 2000 字符");
                    values.add(value);
                }
                rows.add(values);
            }
            if(rows.size()<2)throw new IllegalArgumentException("文件须包含表头和数据");
            return SnapshotData.infer(rows.get(0),rows.subList(1,rows.size()));
        }
    }
    public static SnapshotData csv(byte[] bytes)throws CharacterCodingException {
        if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("CSV 超过 5 MB");
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        if(text.startsWith("\uFEFF"))text=text.substring(1);
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();
        boolean quoted=false,closed=false;
        for(int i=0;i<text.length();i++){
            char ch=text.charAt(i);
            if(quoted){if(ch=='"'){if(i+1<text.length() && text.charAt(i+1)=='"'){cell.append('"');i++;}else{quoted=false;closed=true;}}else cell.append(ch);}
            else if(ch==',' || ch=='\r' || ch=='\n'){
                row.add(cell.toString());cell.setLength(0);closed=false;
                if(row.size()>SnapshotData.MAX_COLUMNS)throw new IllegalArgumentException("CSV 超过 200 列");
                if(ch!=','){if(ch=='\r' && i+1<text.length() && text.charAt(i+1)=='\n')i++;rows.add(row);row=new ArrayList<>();}
            }else if(ch=='"'){if(cell.length()>0 || closed)throw new IllegalArgumentException("CSV 引号格式无效");quoted=true;}
            else {if(closed)throw new IllegalArgumentException("CSV 闭合引号后有非法字符");cell.append(ch);}
            if(cell.length()>SnapshotData.MAX_CELL || rows.size()>SnapshotData.MAX_ROWS+1)throw new IllegalArgumentException("CSV 超过行数或单元格限制");
        }
        if(quoted)throw new IllegalArgumentException("CSV 引号未闭合");
        if(cell.length()>0 || closed || !row.isEmpty()){row.add(cell.toString());rows.add(row);}
        if(rows.size()<2)throw new IllegalArgumentException("CSV 须包含表头和数据");
        return SnapshotData.infer(rows.get(0),rows.subList(1,rows.size()));
    }
    public static JsonNode tree(byte[] bytes,ObjectMapper mapper)throws IOException {
        if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("API 响应超过 5 MB");
        try(var parser=mapper.getFactory().createParser(bytes)){parser.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);return mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).with(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).readTree(parser);}
    }
    public static SnapshotData json(byte[] bytes,String path,ObjectMapper mapper)throws IOException {return json(bytes,path,mapper,false,false);}
    public static SnapshotData json(byte[] bytes,String path,ObjectMapper mapper,boolean paginated)throws IOException {return json(bytes,path,mapper,paginated,false);}
    public static SnapshotData json(byte[] bytes,String path,ObjectMapper mapper,boolean paginated,boolean flatten)throws IOException {
        JsonNode root=tree(bytes,mapper);
        JsonNode array=root;List<JsonNode> parents=new ArrayList<>();parents.add(root);
        if(path!=null && !path.isBlank()){
            if(!path.matches("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*"))throw new IllegalArgumentException("数据路径使用 data.rows 形式");
            for(String key:path.split("\\.")){array=array.path(key);parents.add(array);}
        }
        if(!array.isArray() || array.isEmpty())throw new IllegalArgumentException("API 数据路径须指向非空对象数组");
        if(array.size()>SnapshotData.MAX_ROWS)throw new IllegalArgumentException("API 超过 20000 行");
        if(!paginated)for(JsonNode parent:parents)if(parent.isObject())for(String key:List.of("total","totalCount"))if(parent.path(key).canConvertToLong() && parent.path(key).longValue()>array.size())throw new IllegalArgumentException("接口返回分页数据，请提供完整数据接口");
        JsonNode first=array.get(0);if(!first.isObject())throw new IllegalArgumentException("API 数组元素须为对象");
        Map<String,JsonNode> firstFields=fields(first,flatten);List<String> names=new ArrayList<>(firstFields.keySet());
        List<List<String>> rows=new ArrayList<>();
        for(JsonNode object:array){
            if(!object.isObject())throw new IllegalArgumentException("API 各行字段不一致");Map<String,JsonNode> flattened=fields(object,flatten);
            if(!flattened.keySet().equals(firstFields.keySet()))throw new IllegalArgumentException("API 各行字段不一致");
            List<String> row=new ArrayList<>();
            for(String name:names){JsonNode value=flattened.get(name);if(value==null || value.isContainerNode())throw new IllegalArgumentException("API 嵌套数组无法展开，请先在上游转换为字段");row.add(value.isNull()?null:scalar(value));}
            rows.add(row);
        }
        return SnapshotData.infer(names,rows);
    }
    private static Map<String,JsonNode> fields(JsonNode object,boolean flatten){Map<String,JsonNode> result=new LinkedHashMap<>();if(flatten)flatten(object,"",result,0);else object.fields().forEachRemaining(e->result.put(e.getKey(),e.getValue()));if(result.size()>SnapshotData.MAX_COLUMNS)throw new IllegalArgumentException("API 字段超过 200 列");return result;}
    private static void flatten(JsonNode node,String prefix,Map<String,JsonNode> result,int depth){if(depth>8)throw new IllegalArgumentException("API JSON 嵌套超过 8 层");node.fields().forEachRemaining(e->{String key=prefix.isEmpty()?e.getKey():prefix+"."+e.getKey();if(e.getValue().isObject())flatten(e.getValue(),key,result,depth+1);else result.put(key,e.getValue());});}
    private static String scalar(JsonNode value){
        if(!value.isNumber())return value.asText();
        java.math.BigDecimal number=value.decimalValue();
        if(Math.abs((long)number.scale())>SnapshotData.MAX_CELL || (long)number.precision()+Math.max(0L,-(long)number.scale())>SnapshotData.MAX_CELL)
            throw new IllegalArgumentException("API 数值超过单元格限制");
        return number.toPlainString();
    }
}
