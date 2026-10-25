package com.wanfo.bi.service;

import java.util.*;

/** Sample-based quality indicators. Never includes sample rows in its response. */
public final class DatasetQuality {
    private DatasetQuality(){}
    public static Map<String,Object> summarize(List<String> columns,List<Map<String,Object>> rows){
        List<Map<String,Object>> stats=new ArrayList<>();
        for(String column:columns){int nulls=0;Set<String> distinct=new HashSet<>();for(var row:rows){Object value=row.get(column);if(value==null||value instanceof String s&&s.isBlank())nulls++;else distinct.add(value.getClass().getName()+":"+value);}int present=rows.size()-nulls;stats.add(Map.of("field",column,"nullCount",nulls,"nonNullCount",present,"distinctCount",distinct.size(),"duplicateValueCount",Math.max(0,present-distinct.size()),"nullRate",rows.isEmpty()?0.0:(double)nulls/rows.size()));}
        Set<String> rowKeys=new HashSet<>();int duplicateRows=0;for(var row:rows){String key=new TreeMap<>(row).toString();if(!rowKeys.add(key))duplicateRows++;}
        Map<String,Object> out=new LinkedHashMap<>();out.put("scanMode","sample");out.put("sampleLimit",100);out.put("sampledRows",rows.size());out.put("duplicateRows",duplicateRows);out.put("columns",stats);out.put("note","统计基于最多 100 行当前用户可见样本；不是全表完整性证明。");return out;
    }
    public static Map<String,Object> fullScan(List<String> columns,long rowCount,long[] nullCounts,long[] distinctCounts,long duplicateRows,long costMs){
        if(rowCount<0||nullCounts.length!=columns.size()||distinctCounts.length!=columns.size())throw new IllegalArgumentException("全量质量扫描结果无效");
        List<Map<String,Object>> stats=new ArrayList<>();
        for(int i=0;i<columns.size();i++){long nulls=nullCounts[i],distinct=distinctCounts[i];if(nulls<0||nulls>rowCount||distinct<0||distinct>rowCount-nulls)throw new IllegalArgumentException("全量质量统计超出有效范围");long present=rowCount-nulls;stats.add(Map.of("field",columns.get(i),"nullCount",nulls,"nonNullCount",present,"distinctCount",distinct,"duplicateValueCount",present-distinct,"nullRate",rowCount==0?0.0:(double)nulls/rowCount));}
        Map<String,Object> report=new LinkedHashMap<>();report.put("scanMode","full");report.put("scannedRows",rowCount);report.put("duplicateRows",duplicateRows);report.put("columns",stats);report.put("costMs",Math.max(0,costMs));report.put("note","全量精确扫描；范围遵循当前数据权限。扫描期间数据源可能仍在变化。");return report;
    }
}
