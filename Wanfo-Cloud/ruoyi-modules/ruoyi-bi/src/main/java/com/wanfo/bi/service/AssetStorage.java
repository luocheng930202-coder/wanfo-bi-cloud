package com.wanfo.bi.service;
import java.util.*;
/** Replaceable image storage; the initial implementation uses BI metadata storage. */
public interface AssetStorage {
 List<Map<String,Object>> list();
 Map<String,Object> get(Long id);
 Long save(String name,String image,int bytes,String actor);
 boolean delete(Long id);
 boolean rename(Long id,String name,String expectedName);
}
