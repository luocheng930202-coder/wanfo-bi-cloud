package com.wanfo.bi.service;
import java.util.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wanfo.bi.mapper.BiImageStagingMapper;
import com.wanfo.bi.security.*;
@Service
public class AssetStagingService {
 public record Upload(String name,String image) { }
 public record Release(List<Long> ids) { }
 private static final int MAX_ACTIVE = 30;
 private final BiImageStagingMapper mapper;
 private final AssetStorage storage;
 private final BiResourceAuthorizer resources;
 private final CapabilityProvider caps;
 private final CurrentActorProvider actors;
 public AssetStagingService(BiImageStagingMapper mapper,AssetStorage storage,BiResourceAuthorizer resources,CapabilityProvider caps,CurrentActorProvider actors){
  this.mapper=mapper;this.storage=storage;this.resources=resources;this.caps=caps;this.actors=actors;
 }
 @PostConstruct void initPurge(){purgeExpired();}
 public void purgeExpired(){mapper.purge();}
 private void requireUpload(){
  if(caps.has("bi:asset:add") || caps.has("bi:dashboard:edit") || caps.has("bi:dashboard:add"))return;
  caps.require("bi:dashboard:edit");
 }
 private Long owner(){return actors.currentActor().userId();}
 static int validateImage(String image){
  DashboardContent.normalize("image",new DashboardContent.Content(null,image));
  return Base64.getDecoder().decode(image.substring(image.indexOf(',')+1)).length;
 }
 @Transactional public Map<String,Object> upload(Upload request){
  requireUpload();
  if(request==null || request.image()==null || request.image().isBlank())throw new IllegalArgumentException("请提供图片内容");
  String name=request.name()==null || request.name().isBlank()?"暂存图片":request.name().trim();
  if(name.length()>200)throw new IllegalArgumentException("名称最多 200 字符");
  int bytes=validateImage(request.image());
  Long owner=owner();
  mapper.purge();
  if(mapper.countActive(owner)>=MAX_ACTIVE)throw new IllegalArgumentException("暂存素材过多，请先保存看板或等待过期回收");
  var row=new LinkedHashMap<String,Object>();
  row.put("name",name);row.put("image",request.image());row.put("byteSize",bytes);row.put("owner",owner);row.put("actor",actors.currentActor().username());
  mapper.insert(row);
  return Map.of("id",row.get("id"),"name",name,"image",request.image(),"byteSize",bytes,"expireHours",24);
 }
 public Map<String,Object> detail(Long id){
  requireUpload();
  var row=mapper.get(id,owner());
  if(row==null)throw new IllegalArgumentException("暂存素材不存在或已过期");
  return row;
 }
 @Transactional public Map<String,Object> release(Release request){
  requireUpload();
  if(request==null || request.ids()==null || request.ids().isEmpty())return Map.of("released",0);
  List<Long> ids=request.ids().stream().filter(id->id!=null && id>0).distinct().limit(100).toList();
  if(ids.isEmpty())return Map.of("released",0);
  return Map.of("released",mapper.release(owner(),ids));
 }
 @Transactional public Map<String,Object> promote(Long id,String name){
  caps.require("bi:asset:add");
  var row=mapper.get(id,owner());
  if(row==null)throw new IllegalArgumentException("暂存素材不存在或已过期");
  String finalName=name==null || name.isBlank()?String.valueOf(row.get("name")):name.trim();
  if(finalName.isBlank() || finalName.length()>200)throw new IllegalArgumentException("素材名称须为 1～200 字符");
  String image=String.valueOf(row.get("image"));
  int bytes=((Number)row.get("byteSize")).intValue();
  Long libraryId=storage.save(finalName,image,bytes,actors.currentActor().username());
  resources.register("asset",libraryId);
  mapper.release(owner(),List.of(id));
  return Map.of("id",libraryId);
 }
}
