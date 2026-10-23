package com.wanfo.bi.datasource.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanfo.bi.mapper.BiSourceSnapshotMapper;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class SnapshotRegistry {
    private final BiSourceSnapshotMapper mapper;
    private final ObjectMapper json=new ObjectMapper();
    public SnapshotRegistry(BiSourceSnapshotMapper mapper){this.mapper=mapper;}
    public static boolean supports(String type){return Set.of("api","csv","excel").contains(type==null?"":type.toLowerCase(Locale.ROOT));}
    public SnapshotData load(Long id){return decode(row(id));}
    public BiSourceSnapshotMapper.Row row(Long id){var row=mapper.get(id);if(row==null)throw new IllegalArgumentException("数据快照不存在");return row;}
    public SnapshotData decode(BiSourceSnapshotMapper.Row row){
        try{return new SnapshotData(json.readValue(row.columnsJson(),json.getTypeFactory().constructCollectionType(List.class,SnapshotData.Column.class)),json.readValue(row.rowsJson(),json.getTypeFactory().constructCollectionType(List.class,json.getTypeFactory().constructCollectionType(List.class,String.class))));}
        catch(java.io.IOException e){throw new IllegalStateException("快照读取失败",e);}
    }
}
