package com.wanfo.bi.service;

import java.util.*;

/** Sample-based quality indicators. Never includes sample rows in its response. */
public final class DatasetQuality {
    private DatasetQuality(){}
    public static Map<String,Object> summarize(List<String> columns,List<Map<String,Object>> rows){
        List<Map<String,Object>> stats=new ArrayList<>();
        for(String column:columns){int nulls=0;Set<String> distinct=new HashSet<>();for(var row:rows){Object value=row.get(column);if(value==null||value instanceof String s&&s.isBlank())nulls++;else distinct.add(value.getClass().getName()+":"+value);}int present=rows.size()-nulls;stats.add(Map.of("field",column,"nullCount",nulls,"nonNullCount",present,"distinctCount",distinct.size(),"duplicateValueCount",Math.max(0,present-distinct.size()),"nullRate",rows.isEmpty()?0.0:(double)nulls/rows.size()));}
        Set<String> rowKeys=new HashSet<>();int duplicateRows=0;for(var row:rows){String key=new TreeMap<>(row).toString();if(!rowKeys.add(key))duplicateRows++;}
        Map<String,Object> out=new LinkedHashMap<>();out.put("sampleLimit",100);out.put("sampledRows",rows.size());out.put("duplicateRows",duplicateRows);out.put("columns",stats);out.put("note","统计基于最多 100 行当前用户可见样本；不是全表完整性证明。");return out;
    }
}
