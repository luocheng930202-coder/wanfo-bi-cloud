package com.wanfo.bi.service;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.wanfo.bi.mapper.BiScreenTemplateMapper;
import com.wanfo.bi.security.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ScreenTemplateService {
 public record SaveRequest(String name,String category,String description,Map<String,Object> content) { }
 private final BiScreenTemplateMapper mapper;private final BiResourceAuthorizer resources;private final CapabilityProvider caps;private final CurrentActorProvider actors;private final ObjectMapper json;
 public ScreenTemplateService(BiScreenTemplateMapper mapper,BiResourceAuthorizer resources,CapabilityProvider caps,CurrentActorProvider actors,ObjectMapper json){
  this.mapper=mapper;this.resources=resources;this.caps=caps;this.actors=actors;this.json=json;
 }
 public List<Map<String,Object>> list(){
  caps.require("bi:template:list");
  List<Map<String,Object>> result=new ArrayList<>();
  for(var row:mapper.list()){
   Long id=((Number)row.get("id")).longValue();
   if(!resources.allowed("template",id,"view") && !resources.allowed("template",id,"use")) continue;
   Map<String,Object> item=new LinkedHashMap<>(row);
   item.put("content",decode(String.valueOf(row.get("contentJson"))));
   item.remove("contentJson");
   item.put("kind",kind(item.get("content")));
   item.put("resourceActions",java.util.stream.Stream.of("edit","remove","grant","use").filter(a->resources.allowed("template",id,a)).toList());
   result.add(item);
  }
  return result;
 }
 /** 列表元数据，不含布局 JSON，供模板中心使用。 */
 public List<Map<String,Object>> listSummaries(){
  caps.require("bi:template:list");
  List<Map<String,Object>> result=new ArrayList<>();
  for(var row:mapper.list()){
   Long id=((Number)row.get("id")).longValue();
   if(!resources.allowed("template",id,"view") && !resources.allowed("template",id,"use")) continue;
   Map<String,Object> item=new LinkedHashMap<>();
   item.put("id",id);
   item.put("name",row.get("name"));
   item.put("category",row.get("category"));
   item.put("description",row.get("description"));
   item.put("createBy",row.get("createBy"));
   item.put("createTime",row.get("createTime"));
   item.put("kind",quickKind(String.valueOf(row.get("contentJson"))));
   item.put("resourceActions",java.util.stream.Stream.of("edit","remove","grant","use").filter(a->resources.allowed("template",id,a)).toList());
   result.add(item);
  }
  return result;
 }
 public Map<String,Object> detail(Long id){
  caps.require("bi:template:list");
  if(!resources.allowed("template",id,"view") && !resources.allowed("template",id,"use")) throw new BiAccessException(403,"没有该资源的操作权限");
  var row=mapper.get(id);if(row==null)throw new IllegalArgumentException("模板不存在");
  Map<String,Object> item=new LinkedHashMap<>(row);
  item.put("content",decode(String.valueOf(row.get("contentJson"))));
  item.remove("contentJson");
  item.put("kind",kind(item.get("content")));
  return item;
 }
 @Transactional public Map<String,Object> create(SaveRequest request){
  caps.require("bi:template:add");
  var row=row(request);row.put("actor",actors.currentActor().username());
  mapper.insert(row);Long id=((Number)row.get("id")).longValue();resources.register("template",id);
  return Map.of("id",id);
 }
 @Transactional public Map<String,Object> update(Long id,SaveRequest request){
  caps.require("bi:template:edit");resources.require("template",id,"edit");
  if(mapper.get(id)==null)throw new IllegalArgumentException("模板不存在");
  var row=row(request);row.put("id",id);row.put("actor",actors.currentActor().username());
  if(mapper.update(row)!=1)throw new IllegalArgumentException("模板不存在或已删除");
  return Map.of("id",id);
 }
 @Transactional public Map<String,Object> delete(Long id){
  caps.require("bi:template:remove");resources.require("template",id,"remove");
  if(mapper.delete(id)!=1)throw new IllegalArgumentException("模板不存在或已删除");
  return Map.of("deleted",true);
 }
 public List<Map<String,Object>> grants(Long id){caps.require("bi:template:grant");active(id,"grant");return resources.grants("template",id);}
 @Transactional public void grant(Long id,Long principal,String action,boolean add){caps.require("bi:template:grant");active(id,"grant");resources.change("template",id,principal,action,add);}
 private void active(Long id,String action){resources.require("template",id,"view");resources.require("template",id,action);if(mapper.get(id)==null)throw new IllegalArgumentException("模板不存在");}
 private Map<String,Object> row(SaveRequest request){
  if(request==null || request.name()==null || request.name().isBlank() || request.name().trim().length()>200)throw new IllegalArgumentException("模板名称须为 1～200 字符");
  if(request.category()==null || request.category().isBlank() || request.category().trim().length()>64)throw new IllegalArgumentException("模板分类须为 1～64 字符");
  String description=request.description()==null?"":request.description().trim();
  if(description.length()>500)throw new IllegalArgumentException("模板说明不超过 500 字符");
  String encoded=json.writeValueAsString(validate(request.content()));
  if(encoded.getBytes(StandardCharsets.UTF_8).length>2*1024*1024)throw new IllegalArgumentException("模板内容不能超过 2MB");
  Map<String,Object> row=new HashMap<>();row.put("name",request.name().trim());row.put("category",request.category().trim());row.put("description",description);row.put("contentJson",encoded);return row;
 }
 static Map<String,Object> validate(Map<String,Object> content){
  return LayoutTemplateValidator.validate(content);
 }

 private static String kind(Object content){
  if(content instanceof Map<?,?> map && "datacenter-dashboard-template".equals(String.valueOf(map.get("format")))) return "dashboard";
  return "screen";
 }
 private static String quickKind(String contentJson){
  return contentJson != null && contentJson.contains("datacenter-dashboard-template")?"dashboard":"screen";
 }
 private static Integer asInt(Object v){if(v instanceof Number n)return n.intValue();if(v instanceof String s)try{return Integer.parseInt(s);}catch(NumberFormatException e){return null;}return null;}
 private Map<String,Object> decode(String raw){
  try{return json.readValue(raw,Map.class);}catch(RuntimeException e){throw new IllegalArgumentException("模板内容损坏");}
 }
}
