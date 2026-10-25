package com.wanfo.bi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wanfo.bi.datasource.*;
import com.wanfo.bi.datasource.snapshot.*;
import com.wanfo.bi.domain.BiDatasourceDO;
import com.wanfo.bi.mapper.BiSourceSnapshotMapper;
import com.wanfo.bi.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;

/** Fetch/parse outside the transaction; publish a complete snapshot atomically with optimistic locking. */
@Service
public class SnapshotImportService {
    private SourceRefreshService refreshes;
    @org.springframework.beans.factory.annotation.Autowired
    public void configureRefreshes(SourceRefreshService refreshes){this.refreshes=refreshes;}
    private final DbDataSourceProvider sources;
    private final BiSourceSnapshotMapper snapshots;
    private final SnapshotRegistry registry;
    private final CredentialCipher cipher;
    private final ApiSnapshotClient client;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final TransactionTemplate tx;
    private final ObjectMapper json=new ObjectMapper();
    public SnapshotImportService(DbDataSourceProvider sources,BiSourceSnapshotMapper snapshots,SnapshotRegistry registry,
            CredentialCipher cipher,ApiSnapshotClient client,BiResourceAuthorizer resources,CapabilityProvider capabilities,
            CurrentActorProvider actors,PlatformTransactionManager manager){
        this.sources=sources;this.snapshots=snapshots;this.registry=registry;this.cipher=cipher;this.client=client;
        this.resources=resources;this.capabilities=capabilities;this.actors=actors;this.tx=new TransactionTemplate(manager);
    }
    public record ApiRequest(Long id,String name,String schemaName,String url,String dataPath,Map<String,String> headers,ApiSnapshotClient.Pagination pagination,Integer refreshMinutes,String method,Map<String,Object> body,boolean flatten,ApiSnapshotClient.OAuth oauth){
        public ApiRequest(Long id,String name,String schemaName,String url,String path,Map<String,String> headers){this(id,name,schemaName,url,path,headers,null,0,"GET",null,false,null);}
    }
    public Map<String,Object> api(ApiRequest request)throws Exception {
        var old=authorize(request.id(),"api");
        int interval=request.refreshMinutes()==null?0:request.refreshMinutes();SourceRefreshService.validate(interval);
        String method=request.method()==null?"GET":request.method().toUpperCase(Locale.ROOT);
        if(!Set.of("GET","POST").contains(method))throw new IllegalArgumentException("API 请求方式无效");
        ApiSnapshotClient.OAuth oauth=request.oauth();
        if(old!=null){var saved=json.readValue(cipher.decrypt(old.configCipher()),ApiSnapshotClient.Config.class);if(oauth!=null && (oauth.clientSecret()==null||oauth.clientSecret().isBlank()))oauth=saved.oauth();if(request.headers()==null && Objects.equals(saved.url(),request.url()))request=new ApiRequest(request.id(),request.name(),request.schemaName(),request.url(),request.dataPath(),saved.headers(),request.pagination(),request.refreshMinutes(),method,request.body(),request.flatten(),oauth);}
        ApiSnapshotClient.Config config=new ApiSnapshotClient.Config(request.url(),request.dataPath(),request.headers(),request.pagination(),method,request.body(),request.flatten(),oauth);
        if(old!=null && request.headers()==null){
            var saved=json.readValue(cipher.decrypt(old.configCipher()),ApiSnapshotClient.Config.class);
            if(!Objects.equals(saved.url(),request.url()))throw new IllegalArgumentException("修改 API 地址时须重新提供请求头");
            config=new ApiSnapshotClient.Config(request.url(),request.dataPath(),saved.headers(),request.pagination(),method,request.body(),request.flatten(),oauth);
        }
        var data=ApiSnapshotReader.read(client,config,json);
        final var finalRequest=request;String encoded=cipher.encrypt(json.writeValueAsString(config));
        return tx.execute(status->{try{var result=publish(finalRequest.id(),finalRequest.name(),finalRequest.schemaName(),"api",data,encoded,null,old);if(refreshes!=null)refreshes.configure(((Number)result.get("id")).longValue(),interval,actors.currentActor().username());return result;}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("API 快照保存失败",e);}});
    }
    public Map<String,Object> refresh(Long id)throws Exception {
        var old=authorize(id,"api");
        var config=json.readValue(cipher.decrypt(old.configCipher()),ApiSnapshotClient.Config.class);
        var data=ApiSnapshotReader.read(client,config,json);
        return publish(id,null,null,"api",data,old.configCipher(),null,old);
    }
    public Map<String,Object> file(Long id,String name,String alias,String filename,String sheet,byte[] bytes)throws Exception {return file(id,name,alias,filename,sheet,bytes,"replace",null);}
    public Map<String,Object> previewFile(Long id,String filename,String sheet,byte[] bytes)throws Exception {
        var old=authorize(id,filename.toLowerCase(Locale.ROOT).endsWith(".csv")?"csv":"excel");var data=SnapshotParser.file(bytes,filename,sheet);
        return Map.of("columns",data.columns(),"rows",data.rows().subList(0,Math.min(10,data.rows().size())),"rowCount",data.rows().size(),"existingColumns",old==null?List.of():registry.decode(old).columns());
    }
    public Map<String,Object> file(Long id,String name,String alias,String filename,String sheet,byte[] bytes,String mode,String columnTypes)throws Exception {return file(id,name,alias,filename,sheet,bytes,mode,columnTypes,"reject");}
    public Map<String,Object> file(Long id,String name,String alias,String filename,String sheet,byte[] bytes,String mode,String columnTypes,String invalidMode)throws Exception {
        return file(id,name,alias,filename,sheet,bytes,mode,columnTypes,invalidMode,null,"none",null);
    }
    public Map<String,Object> file(Long id,String name,String alias,String filename,String sheet,byte[] bytes,String mode,String columnTypes,String invalidMode,String columnMapping,String dedupeMode,String dedupeKeys)throws Exception {
        String kind=filename.toLowerCase(Locale.ROOT).endsWith(".csv")?"csv":"excel";
        var old=authorize(id,kind);
        var data=mapColumns(SnapshotParser.file(bytes,filename,sheet),columnMapping);
        if(columnTypes!=null && !columnTypes.isBlank()){
            if(columnTypes.length()>32768)throw new IllegalArgumentException("字段类型配置过大");
            Map<String,String> types=json.readValue(columnTypes,json.getTypeFactory().constructMapType(Map.class,String.class,String.class));
            if(!data.columns().stream().map(SnapshotData.Column::name).toList().containsAll(types.keySet()))throw new IllegalArgumentException("字段类型配置与文件不匹配");
            data=new SnapshotData(data.columns().stream().map(c->new SnapshotData.Column(c.name(),types.getOrDefault(c.name(),c.type()))).toList(),data.rows());
        }
        if(mode==null)mode="replace";
        if(!Set.of("replace","append","replace-structure").contains(mode))throw new IllegalArgumentException("文件更新方式无效");
        if(!Set.of("reject","skip").contains(invalidMode))throw new IllegalArgumentException("异常行处理方式无效");
        List<Map<String,Object>> errors=new ArrayList<>();List<List<String>> validRows=new ArrayList<>();
        for(int r=0;r<data.rows().size();r++){var row=data.rows().get(r);String issue=null;for(int c=0;c<row.size();c++)try{data.value(c,row.get(c));}catch(IllegalArgumentException e){issue="第 "+(r+2)+" 行，字段「"+data.columns().get(c).name()+"」："+e.getMessage();break;}if(issue==null)validRows.add(row);else if(errors.size()<100)errors.add(Map.of("row",r+2,"message",issue));}
        int rejected=data.rows().size()-validRows.size();
        if(rejected>0 && "reject".equals(invalidMode))throw new IllegalArgumentException("发现 "+rejected+" 行字段类型不匹配；首个问题："+errors.get(0).get("message"));
        if(validRows.isEmpty())throw new IllegalArgumentException("没有可导入的有效数据行");
        data=new SnapshotData(data.columns(),validRows);
        if("append".equals(mode)){
            if(old==null)throw new IllegalArgumentException("追加需要选择已有文件数据源");
            var prior=registry.decode(old);data=alignToSchema(data,prior).compatibleWith(prior);var rows=new ArrayList<List<String>>(prior.rows());rows.addAll(data.rows());data=new SnapshotData(prior.columns(),rows);
        }
        else if(old!=null && !"replace-structure".equals(mode))data=alignToSchema(data,registry.decode(old));
        var deduplicated=deduplicate(data,dedupeMode,dedupeKeys);data=deduplicated.data();
        String safe=filename.replace('\\','/');safe=safe.substring(safe.lastIndexOf('/')+1);
        if(safe.length()>200)safe=safe.substring(safe.length()-200);
        boolean structureChange="replace-structure".equals(mode);
        Map<String,Object> result=new LinkedHashMap<>(publish(id,name,alias,kind,data,null,safe,old,structureChange));result.put("rejectedRows",rejected);result.put("rowErrors",errors);result.put("duplicateRows",deduplicated.removed());return result;
    }
    private SnapshotData mapColumns(SnapshotData data,String mappingJson)throws Exception {
        if(mappingJson==null||mappingJson.isBlank())return data;
        if(mappingJson.length()>32768)throw new IllegalArgumentException("字段映射配置过大");
        Map<String,String> mapping=json.readValue(mappingJson,json.getTypeFactory().constructMapType(Map.class,String.class,String.class));
        Set<String> sourceNames=new HashSet<>(data.columns().stream().map(SnapshotData.Column::name).toList());if(!sourceNames.containsAll(mapping.keySet()))throw new IllegalArgumentException("字段映射包含不存在的源字段");
        List<Integer> indexes=new ArrayList<>();List<SnapshotData.Column> columns=new ArrayList<>();Set<String> targets=new HashSet<>();
        for(int i=0;i<data.columns().size();i++){
            var source=data.columns().get(i);String target=mapping.getOrDefault(source.name(),source.name());if(target==null||target.isBlank())continue;target=target.trim();
            String key=target.toLowerCase(Locale.ROOT);if(!targets.add(key))throw new IllegalArgumentException("多个源字段不能映射到同一个目标字段："+target);
            indexes.add(i);columns.add(new SnapshotData.Column(target,source.type()));
        }
        if(columns.isEmpty())throw new IllegalArgumentException("至少保留一个目标字段");
        List<List<String>> rows=data.rows().stream().map(row->{List<String> mapped=new ArrayList<>(indexes.size());for(Integer i:indexes)mapped.add(row.get(i));return mapped;}).toList();
        return new SnapshotData(columns,rows);
    }
    private SnapshotData alignToSchema(SnapshotData incoming,SnapshotData schema){
        List<String> names=schema.columns().stream().map(SnapshotData.Column::name).toList();Map<String,Integer> indexes=new HashMap<>();for(int i=0;i<incoming.columns().size();i++){String field=incoming.columns().get(i).name();if(!names.contains(field))throw new IllegalArgumentException("追加字段「"+field+"」未映射到已有结构；新增字段请使用结构替换");indexes.put(field,i);}
        List<List<String>> rows=incoming.rows().stream().map(source->{List<String> aligned=new ArrayList<>(names.size());for(String name:names){Integer index=indexes.get(name);aligned.add(index==null?null:source.get(index));}return aligned;}).toList();
        return new SnapshotData(schema.columns(),rows);
    }
    private record DedupeResult(SnapshotData data,int removed){}
    private DedupeResult deduplicate(SnapshotData data,String mode,String keysJson)throws Exception {
        String selected=mode==null||mode.isBlank()?"none":mode.trim();if(!Set.of("none","all-columns","keys").contains(selected))throw new IllegalArgumentException("去重策略无效");
        if("none".equals(selected))return new DedupeResult(data,0);
        List<String> keys;
        if("all-columns".equals(selected))keys=data.columns().stream().map(SnapshotData.Column::name).toList();
        else {if(keysJson==null||keysJson.isBlank()||keysJson.length()>8192)throw new IllegalArgumentException("请选择去重键字段");keys=json.readValue(keysJson,json.getTypeFactory().constructCollectionType(List.class,String.class));if(keys.isEmpty()||!data.columns().stream().map(SnapshotData.Column::name).toList().containsAll(keys)||new HashSet<>(keys).size()!=keys.size())throw new IllegalArgumentException("去重键包含重复或不存在的字段");}
        List<String> names=data.columns().stream().map(SnapshotData.Column::name).toList();List<Integer> indexes=keys.stream().map(names::indexOf).toList();
        LinkedHashMap<List<String>,List<String>> unique=new LinkedHashMap<>();for(List<String> row:data.rows()){List<String> key=indexes.stream().map(row::get).toList();unique.remove(key);unique.put(key,row);}
        int removed=data.rows().size()-unique.size();return new DedupeResult(new SnapshotData(data.columns(),new ArrayList<>(unique.values())),removed);
    }
    private BiSourceSnapshotMapper.Row authorize(Long id,String kind){
        capabilities.require(id==null?"bi:datasource:add":"bi:datasource:edit");
        if(id==null)return null;
        resources.require("datasource",id,"edit");
        var source=sources.getRow(id);
        if(source==null || !kind.equals(source.getType()))throw new IllegalArgumentException("不能改变已保存的数据源类型");
        return registry.row(id);
    }
    private Map<String,Object> publish(Long id,String name,String alias,String kind,SnapshotData data,String config,String filename,BiSourceSnapshotMapper.Row old)throws Exception {
        return publish(id,name,alias,kind,data,config,filename,old,false);
    }
    private Map<String,Object> publish(Long id,String name,String alias,String kind,SnapshotData data,String config,String filename,BiSourceSnapshotMapper.Row old,boolean structureChange)throws Exception {
        if(old==null && (name==null || name.isBlank() || name.length()>100 || alias==null || !alias.matches("[a-z][a-z0-9_]{0,30}")))throw new IllegalArgumentException("名称或 Schema 别名无效");
        if(old!=null && !structureChange)data=data.compatibleWith(registry.decode(old));
        String columns=json.writeValueAsString(data.columns()),rows=json.writeValueAsString(data.rows());
        // Keep each DB packet bounded even when JSON escaping expands the input.
        if(columns.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+rows.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>6*1024*1024)throw new IllegalArgumentException("快照序列化后过大");
        int count=data.rows().size();
        return tx.execute(status->{
            Long target=id;
            if(old==null){
                if(sources.countBySchemaName(alias)>0)throw new IllegalArgumentException("Schema 别名已被占用");
                var source=new BiDatasourceDO();source.setName(name);source.setSchemaName(alias);source.setType(kind);
                source.setJdbcUrl("snapshot:"+kind);source.setDriverClass("");source.setUsername("");source.setPassword("");source.setStatus("0");source.setDelFlag("0");
                source.setPoolMaxTotal(1);source.setPoolMinIdle(0);source.setQueryTimeout(30);source.setCreateBy(actors.currentActor().username());source.setUpdateBy(source.getCreateBy());
                sources.insert(source);target=source.getId();resources.register("datasource",target);
            }
            var row=new BiSourceSnapshotMapper.Row(target,kind,config,columns,rows,count,old==null?1:old.version(),filename,null);
            if(old==null)snapshots.insert(row);else if(snapshots.replace(row)!=1)throw new IllegalStateException("快照已被其他操作更新，请重新刷新");
            return Map.<String,Object>of("id",target,"rowCount",count,"version",old==null?1:old.version()+1,"columns",registry.decode(row).columns());
        });
    }
    public Map<String,Object> info(Long id){
        capabilities.require("bi:datasource:edit");resources.require("datasource",id,"edit");
        if(sources.getRow(id)==null)throw new IllegalArgumentException("数据源不存在");
        var row=registry.row(id);Map<String,Object> result=new LinkedHashMap<>();
        result.put("kind",row.kind());result.put("rowCount",row.rowCount());result.put("version",row.version());result.put("fileName",row.fileName());result.put("refreshedAt",row.refreshedAt());
        if("api".equals(row.kind()))try{
            var config=json.readValue(cipher.decrypt(row.configCipher()),ApiSnapshotClient.Config.class);
            result.put("url",config.url());result.put("dataPath",config.dataPath());result.put("headersSet",config.headers()!=null && !config.headers().isEmpty());result.put("pagination",config.pagination());result.put("method",config.method()==null?"GET":config.method());result.put("body",config.body());result.put("flatten",config.flatten());if(config.oauth()!=null)result.put("oauth",Map.of("tokenUrl",config.oauth().tokenUrl(),"clientId",config.oauth().clientId(),"scope",config.oauth().scope()==null?"":config.oauth().scope(),"tokenField",config.oauth().tokenField()==null?"access_token":config.oauth().tokenField(),"secretSet",config.oauth().clientSecret()!=null));if(refreshes!=null)result.put("schedule",refreshes.status(id));
        }catch(java.io.IOException e){throw new IllegalStateException("API 配置读取失败");}
        return result;
    }
}
