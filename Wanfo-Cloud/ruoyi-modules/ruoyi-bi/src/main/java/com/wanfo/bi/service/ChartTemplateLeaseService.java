package com.wanfo.bi.service;
import java.util.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wanfo.bi.mapper.BiChartMapper;
import com.wanfo.bi.mapper.BiChartTemplateLeaseMapper;
import com.wanfo.bi.security.*;
@Service
public class ChartTemplateLeaseService {
 public record Release(List<Long> chartIds) { }
 private final BiChartTemplateLeaseMapper mapper;
 private final BiChartMapper charts;
 private final CapabilityProvider caps;
 private final CurrentActorProvider actors;
 public ChartTemplateLeaseService(BiChartTemplateLeaseMapper mapper,BiChartMapper charts,CapabilityProvider caps,CurrentActorProvider actors){
  this.mapper=mapper;this.charts=charts;this.caps=caps;this.actors=actors;
 }
 @PostConstruct void purgeExpired(){purge();}
 private void requireManage(){
  if(caps.has("bi:dashboard:add") || caps.has("bi:dashboard:edit") || caps.has("bi:chart:add"))return;
  caps.require("bi:dashboard:edit");
 }
 private Long owner(){return actors.currentActor().userId();}
 void register(Long chartId){
  if(chartId==null || chartId<1)return;
  mapper.insert(chartId,owner(),actors.currentActor().username());
 }
 @Transactional public Map<String,Object> commit(Release request){
  requireManage();
  if(request==null || request.chartIds()==null || request.chartIds().isEmpty())return Map.of("committed",0);
  List<Long> ids=request.chartIds().stream().filter(id->id!=null && id>0).distinct().limit(50).toList();
  if(ids.isEmpty())return Map.of("committed",0);
  return Map.of("committed",mapper.deleteMany(owner(),ids));
 }
 @Transactional public Map<String,Object> release(Release request){
  requireManage();
  if(request==null || request.chartIds()==null || request.chartIds().isEmpty())return Map.of("released",0);
  int released=0;
  String actor=actors.currentActor().username();
  for(Long id:request.chartIds().stream().filter(i->i!=null && i>0).distinct().limit(50).toList()){
   if(mapper.find(owner(),id)==null)continue;
   if(charts.delete(id,actor)==1){mapper.delete(owner(),id);released++;}
   else mapper.delete(owner(),id);
  }
  return Map.of("released",released);
 }
 public void purge(){
  for(Long id:mapper.expired()){
   try{
    charts.delete(id,"system");
   }catch(RuntimeException ignored){/* chart may already be gone */}
   mapper.deleteByChartId(id);
  }
 }
}
