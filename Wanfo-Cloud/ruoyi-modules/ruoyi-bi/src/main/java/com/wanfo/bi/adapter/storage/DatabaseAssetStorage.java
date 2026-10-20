package com.wanfo.bi.adapter.storage;
import java.util.*;
import org.springframework.stereotype.Component;
import com.wanfo.bi.mapper.BiAssetMapper;
import com.wanfo.bi.service.AssetStorage;
@Component
public class DatabaseAssetStorage implements AssetStorage {
 private final BiAssetMapper mapper;
 public DatabaseAssetStorage(BiAssetMapper mapper){this.mapper=mapper;}
 public List<Map<String,Object>> list(){return mapper.list();}
 public Map<String,Object> get(Long id){return mapper.get(id);}
 public Long save(String name,String image,int bytes,String actor){var row=new HashMap<String,Object>();row.put("name",name);row.put("image",image);row.put("byteSize",bytes);row.put("actor",actor);mapper.insert(row);return ((Number)row.get("id")).longValue();}
 public boolean rename(Long id,String name,String expectedName){return mapper.rename(id,name,expectedName)==1;}
 public boolean delete(Long id){return mapper.delete(id)==1;}
}
