package com.wanfo.bi.service;
import com.wanfo.bi.mapper.*;
import com.wanfo.bi.datasource.*;
import com.wanfo.bi.datasource.snapshot.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Server-owned maintenance only refreshes explicitly scheduled API snapshots; it never impersonates users. */
@Service
public class SourceRefreshService {
    private final BiSourceRefreshMapper schedules;private final BiSourceSnapshotMapper snapshots;private final SnapshotRegistry registry;
    private final DbDataSourceProvider sources;private final CredentialCipher cipher;private final ApiSnapshotClient client;private final TransactionTemplate tx;
    private final ObjectMapper json=new ObjectMapper();
    private final java.util.concurrent.ThreadPoolExecutor workers=new java.util.concurrent.ThreadPoolExecutor(2,2,0,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(2),r->{var thread=new Thread(r,"bi-source-refresh");thread.setDaemon(true);return thread;});
    @jakarta.annotation.PreDestroy public void close(){workers.shutdownNow();}
    @Value("${bi.external-source.scheduling-enabled:true}") private boolean enabled=true;
    public SourceRefreshService(BiSourceRefreshMapper schedules,BiSourceSnapshotMapper snapshots,SnapshotRegistry registry,DbDataSourceProvider sources,CredentialCipher cipher,ApiSnapshotClient client,PlatformTransactionManager manager){this.schedules=schedules;this.snapshots=snapshots;this.registry=registry;this.sources=sources;this.cipher=cipher;this.client=client;this.tx=new TransactionTemplate(manager);}
    public void configure(Long id,int minutes,String actor){validate(minutes);schedules.configure(id,minutes,actor);}
    public static void validate(int minutes){if(minutes!=0 && (minutes<5 || minutes>1440))throw new IllegalArgumentException("定时刷新间隔为 5～1440 分钟，0 表示关闭");}
    public Object status(Long id){return schedules.status(id);}
    @Scheduled(fixedDelayString="${bi.external-source.poll-ms:60000}",initialDelayString="${bi.external-source.poll-ms:60000}")
    public void tick(){
        if(!enabled)return;
        for(Long id:schedules.due())try{workers.execute(()->run(id));}catch(java.util.concurrent.RejectedExecutionException ignored){break;}
    }
    private void run(Long id){
            String token=UUID.randomUUID().toString();if(schedules.claim(id,token)!=1)return;
            try{refresh(id,token);schedules.finish(id,token,"success");}
            catch(Exception failure){schedules.finish(id,token,"failed: previous snapshot retained");org.slf4j.LoggerFactory.getLogger(getClass()).warn("API snapshot scheduled refresh failed for source {}",id);}
    }
    void refresh(Long id,String token)throws Exception {
        var source=sources.getRow(id);if(source==null || !"0".equals(source.getStatus()) || !"api".equals(source.getType()))throw new IllegalArgumentException("数据源不可刷新");
        var old=registry.row(id);var config=json.readValue(cipher.decrypt(old.configCipher()),ApiSnapshotClient.Config.class);
        var data=ApiSnapshotReader.read(client,config,json).compatibleWith(registry.decode(old));String rows=json.writeValueAsString(data.rows());
        if(rows.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>6*1024*1024)throw new IllegalArgumentException("快照过大");
        var next=new BiSourceSnapshotMapper.Row(id,old.kind(),old.configCipher(),old.columnsJson(),rows,data.rows().size(),old.version(),null,null);
        tx.execute(status->{if(schedules.owns(id,token)!=1 || snapshots.replace(next)!=1)throw new IllegalStateException("刷新任务已变化或快照版本冲突");return null;});
    }
}
