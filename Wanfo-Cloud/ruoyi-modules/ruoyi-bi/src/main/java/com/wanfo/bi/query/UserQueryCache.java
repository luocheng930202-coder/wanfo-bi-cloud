package com.wanfo.bi.query;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.math.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** In-process, bounded, user-scoped result cache. Never stores failures or diagnostic SQL. */
@Component
public class UserQueryCache {
    private record Entry(Long dataset,long created,long expires,Map<String,Object> value,int bytes){}
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(16,.75f,true);
    private final AtomicLong epoch=new AtomicLong();
    private long bytes,hits,misses,evictions;
    private final ObjectMapper json=new ObjectMapper();
    public long epoch(){return epoch.get();}
    public String key(Object fingerprint){
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(fingerprint)));}
        catch(Exception e){return null;}
    }
    public synchronized Map<String,Object> get(String key){
        var entry=entries.get(key);long now=System.currentTimeMillis();
        if(entry==null){misses++;return null;}
        if(entry.expires<=now){entries.remove(key);bytes-=entry.bytes;misses++;return null;}
        hits++;var copy=copyMap(entry.value);copy.put("cache",Map.of("hit",true,"ageMs",now-entry.created,"scope","user","nodeLocal",true));return copy;
    }
    public synchronized void put(String key,Long dataset,int ttl,long expectedEpoch,Map<String,Object> value){
        if(expectedEpoch!=epoch.get())return;
        try{
            var safe=new LinkedHashMap<>(value);for(String field:List.of("executedSql","params","queryDiagnostics","datasetName","cache"))safe.remove(field);
            var snapshot=copyMap(safe);int size=json.writeValueAsBytes(snapshot).length;
            if(size>512*1024)return;
            var old=entries.remove(key);if(old!=null)bytes-=old.bytes;
            long now=System.currentTimeMillis();entries.put(key,new Entry(dataset,now,now+Math.min(300,Math.max(5,ttl))*1000L,snapshot,size));bytes+=size;
            while(entries.size()>200 || bytes>8L*1024*1024){var iterator=entries.entrySet().iterator();var victim=iterator.next().getValue();bytes-=victim.bytes;iterator.remove();evictions++;}
        }catch(RuntimeException ignored){/* Unsupported JDBC cell types do not make a successful query fail. */}
    }
    public synchronized void clear(Long dataset){epoch.incrementAndGet();var iterator=entries.values().iterator();while(iterator.hasNext()){var e=iterator.next();if(dataset==null || dataset.equals(e.dataset)){bytes-=e.bytes;iterator.remove();}}}
    public synchronized Map<String,Object> stats(){return Map.of("entries",entries.size(),"bytes",bytes,"hits",hits,"misses",misses,"evictions",evictions,"scope","node-local-user","maxEntries",200,"maxBytes",8*1024*1024);}
    private static Map<String,Object> copyMap(Map<String,Object> input){Map<String,Object> result=new LinkedHashMap<>();input.forEach((key,value)->result.put(key,copy(value)));return result;}
    private static Object copy(Object value){
        if(value==null || value instanceof String || value instanceof Boolean || value instanceof BigDecimal || value instanceof BigInteger || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double)return value;
        if(value instanceof Map<?,?> map){Map<String,Object> result=new LinkedHashMap<>();map.forEach((key,item)->result.put((String)key,copy(item)));return result;}
        if(value instanceof List<?> list){List<Object> result=new ArrayList<>();list.forEach(item->result.add(copy(item)));return result;}
        if(value instanceof java.sql.Timestamp timestamp){var result=new java.sql.Timestamp(timestamp.getTime());result.setNanos(timestamp.getNanos());return result;}
        if(value instanceof java.sql.Date date)return new java.sql.Date(date.getTime());
        if(value instanceof java.sql.Time time)return new java.sql.Time(time.getTime());
        throw new IllegalArgumentException("Unsupported cached cell type");
    }
}
