package com.wanfo.bi.service;
import com.wanfo.bi.mapper.BiResourceHistoryMapper;
import org.springframework.stereotype.Component;
@Component
public class ResourceHistoryStore {
    private final BiResourceHistoryMapper mapper;
    public ResourceHistoryStore(BiResourceHistoryMapper mapper){this.mapper=mapper;}
    public void record(String type,Long id,int version,String operation,String definition,String actor){
        if(definition==null)return;
        mapper.insert(new BiResourceHistoryMapper.Row(type,id,version,operation,definition,actor==null?"unknown":actor,null));
        mapper.prune(type,id,version);
    }
}
