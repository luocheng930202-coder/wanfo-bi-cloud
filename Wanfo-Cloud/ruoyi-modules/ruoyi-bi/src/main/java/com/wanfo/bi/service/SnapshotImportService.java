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
        authorize(id,filename.toLowerCase(Locale.ROOT).endsWith(".csv")?"csv":"excel");var data=SnapshotParser.file(bytes,filename,sheet);
        return Map.of("columns",data.columns(),"rows",data.rows().subList(0,Math.min(10,data.rows().size())),"rowCount",data.rows().size());
    }
    public Map<String,Object> file(Long id,String name,String alias,String filename,String sheet,byte[] bytes,String mode,String columnTypes)throws Exception {
        String kind=filename.toLowerCase(Locale.ROOT).endsWith(".csv")?"csv":"excel";
        var old=authorize(id,kind);
        var data=SnapshotParser.file(bytes,filename,sheet);
        if(columnTypes!=null && !columnTypes.isBlank()){
            if(columnTypes.length()>32768)throw new IllegalArgumentException("字段类型配置过大");
            Map<String,String> types=json.readValue(columnTypes,json.getTypeFactory().constructMapType(Map.class,String.class,String.class));
            if(!data.columns().stream().map(SnapshotData.Column::name).toList().containsAll(types.keySet()))throw new IllegalArgumentException("字段类型配置与文件不匹配");
            data=new SnapshotData(data.columns().stream().map(c->new SnapshotData.Column(c.name(),types.getOrDefault(c.name(),c.type()))).toList(),data.rows());
            for(var row:data.rows())for(int i=0;i<row.size();i++)data.value(i,row.get(i));
        }
        if(mode==null)mode="replace";
        if(!Set.of("replace","append").contains(mode))throw new IllegalArgumentException("文件更新方式无效");
        if("append".equals(mode)){
            if(old==null)throw new IllegalArgumentException("追加需要选择已有文件数据源");
            var prior=registry.decode(old);data=data.compatibleWith(prior);var rows=new ArrayList<List<String>>(prior.rows());rows.addAll(data.rows());data=new SnapshotData(prior.columns(),rows);
        }
        String safe=filename.replace('\\','/');safe=safe.substring(safe.lastIndexOf('/')+1);
        if(safe.length()>200)safe=safe.substring(safe.length()-200);
        return publish(id,name,alias,kind,data,null,safe,old);
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
        if(old==null && (name==null || name.isBlank() || name.length()>100 || alias==null || !alias.matches("[a-z][a-z0-9_]{0,30}")))throw new IllegalArgumentException("名称或 Schema 别名无效");
        if(old!=null)data=data.compatibleWith(registry.decode(old));
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
