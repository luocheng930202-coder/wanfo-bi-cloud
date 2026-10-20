package com.wanfo.bi.service;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import com.wanfo.bi.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AssetService {
 public record Upload(String name,String image) { }
 private final AssetStorage storage;private final BiResourceAuthorizer resources;private final CapabilityProvider caps;private final CurrentActorProvider actors;
 public AssetService(AssetStorage storage,BiResourceAuthorizer resources,CapabilityProvider caps,CurrentActorProvider actors){this.storage=storage;this.resources=resources;this.caps=caps;this.actors=actors;}
 public Map<String,Object> list(int page){return list(page,"");}
 public Map<String,Object> list(int page,String query){caps.require("bi:asset:list");if(page<1 || page>100000)throw new IllegalArgumentException("页码无效");if(query!=null && query.length()>200)throw new IllegalArgumentException("搜索内容最多 200 字符");String search=query==null?"":query.trim().toLowerCase(Locale.ROOT);
  var all=storage.list().stream().filter(r->resources.allowed("asset",((Number)r.get("id")).longValue(),"view")).filter(r->String.valueOf(r.get("name")).toLowerCase(Locale.ROOT).contains(search)).map(r->{var item=new LinkedHashMap<String,Object>(r);Long id=((Number)r.get("id")).longValue();item.put("resourceActions",java.util.stream.Stream.of("edit","remove","grant").filter(a->resources.allowed("asset",id,a)).toList());return item;}).toList();int start=Math.min((page-1)*20,all.size());return Map.of("items",all.subList(start,Math.min(start+20,all.size())),"total",all.size());}
 public record Rename(String name,String expectedName) { }
 private void active(Long id,String action){resources.require("asset",id,"view");resources.require("asset",id,action);if(storage.get(id)==null)throw new IllegalArgumentException("素材不存在");}
 @Transactional public Map<String,Object> rename(Long id,Rename request){caps.require("bi:asset:edit");active(id,"edit");if(request==null || request.name()==null || request.name().isBlank() || request.name().trim().length()>200 || request.expectedName()==null || request.expectedName().length()>200)throw new IllegalArgumentException("素材名称无效，请刷新后重试");if(!storage.rename(id,request.name().trim(),request.expectedName()))throw new BiAccessException(409,"素材已被修改，请刷新后重试");return Map.of("renamed",true);}
 public List<Map<String,Object>> grants(Long id){caps.require("bi:asset:grant");active(id,"grant");return resources.grants("asset",id);}
 @Transactional public void grant(Long id,Long principal,String action,boolean add){caps.require("bi:asset:grant");active(id,"grant");resources.change("asset",id,principal,action,add);}
 public Map<String,Object> detail(Long id){caps.require("bi:asset:list");resources.require("asset",id,"view");var row=storage.get(id);if(row==null)throw new IllegalArgumentException("素材不存在");return row;}
 static int validate(String image){
  if(image==null || !image.startsWith("data:image/png;base64,") && !image.startsWith("data:image/jpeg;base64,"))throw new IllegalArgumentException("素材库仅支持 PNG/JPEG 图片");
  DashboardContent.normalize("image",new DashboardContent.Content(null,image));
  byte[] bytes=Base64.getDecoder().decode(image.substring(image.indexOf(',')+1));
  try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
   var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("图片无法解码");var reader=readers.next();
   try{reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1 || h<1 || w>4096 || h>4096 || (long)w*h>16000000)throw new IllegalArgumentException("图片尺寸不超过 4096，像素不超过 1600 万");if(reader.read(0)==null)throw new IllegalArgumentException("图片无法解码");}finally{reader.dispose();}
  }catch(IOException e){throw new IllegalArgumentException("图片损坏或无法解码");}
  return bytes.length;
 }
 @Transactional public Map<String,Object> upload(Upload request){caps.require("bi:asset:add");if(request==null || request.name()==null || request.name().isBlank() || request.name().trim().length()>200)throw new IllegalArgumentException("素材名称须为 1～200 字符");int bytes=validate(request.image());Long id=storage.save(request.name().trim(),request.image(),bytes,actors.currentActor().username());resources.register("asset",id);return Map.of("id",id);}
 @Transactional public Map<String,Object> delete(Long id){caps.require("bi:asset:remove");resources.require("asset",id,"remove");if(!storage.delete(id))throw new IllegalArgumentException("素材不存在或已删除");return Map.of("deleted",true);}
}
