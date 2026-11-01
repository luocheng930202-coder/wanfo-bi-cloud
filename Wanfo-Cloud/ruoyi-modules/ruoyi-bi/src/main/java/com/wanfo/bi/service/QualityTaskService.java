package com.wanfo.bi.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.sql.SQLRecoverableException;
import java.sql.SQLNonTransientConnectionException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.ObjectMapper;

import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.mapper.QualityTaskMapper;
import com.wanfo.bi.security.ActorScope;
import com.wanfo.bi.security.BiAccessException;
import com.wanfo.bi.security.BiActor;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.LiveActorResolver;

@Service
public class QualityTaskService {
    public record CreateTask(String name,Long ruleSetId,String scanScope,Integer timeoutSeconds,String scheduleType,Integer intervalMinutes,Integer retryLimit){}
    private static final Set<String> EXECUTABLE_RULES=Set.of("NOT_NULL","UNIQUE","NULL_RATE_MAX","RANGE","REGEX","FRESHNESS","RECONCILIATION");
    private static final int VALUE_RULE_SAMPLE_LIMIT=10_000;
    private final QualityTaskMapper mapper;
    private final BiDatasetTableMapper datasets;
    private final QualityRuleVersionService versions;
    private final DatasetService datasetService;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final LiveActorResolver live;
    private final QualityAlertService alerts;
    private final com.wanfo.bi.mapper.QualityAlertMapper alertMapper;
    private final TaskPoolGateService poolGate;
    private final ExecutorService executor;
    private final DataSource platformDataSource;
    private final ObjectMapper json;
    private final ScheduledExecutorService retryTimer;
    private final int issueSlaHours;
    private final Map<Long,Future<?>> runFutures=new ConcurrentHashMap<>();
    private final Map<Long,ScheduledFuture<?>> retryFutures=new ConcurrentHashMap<>();

    public QualityTaskService(QualityTaskMapper mapper,BiDatasetTableMapper datasets,QualityRuleVersionService versions,
            DatasetService datasetService,BiResourceAuthorizer resources,
            CapabilityProvider capabilities,CurrentActorProvider actors,LiveActorResolver live,
            QualityAlertService alerts,com.wanfo.bi.mapper.QualityAlertMapper alertMapper,TaskPoolGateService poolGate,
            @Qualifier("biWarehouseExecutor") ExecutorService executor,DataSource platformDataSource,ObjectMapper json,
            @Qualifier("biQualityRetryTimer") ScheduledExecutorService retryTimer,
            @org.springframework.beans.factory.annotation.Value("${bi.quality.issue-sla-hours:48}") int issueSlaHours) {
        this.mapper=mapper;this.datasets=datasets;this.versions=versions;this.datasetService=datasetService;
        this.resources=resources;this.capabilities=capabilities;this.actors=actors;this.live=live;
        this.alerts=alerts;this.alertMapper=alertMapper;this.poolGate=poolGate;
        this.executor=executor;this.platformDataSource=platformDataSource;this.json=json;this.retryTimer=retryTimer;
        this.issueSlaHours=Math.max(1,Math.min(issueSlaHours,720));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedRuns(){int recovered=mapper.recoverInterruptedRuns();if(recovered>0)org.slf4j.LoggerFactory.getLogger(QualityTaskService.class).warn("Marked {} unfinished quality run(s) interrupted after service restart",recovered);}

    public List<Map<String,Object>> list(Long datasetId){requireDataset(datasetId,false);return mapper.listTasks(datasetId);}

    @Transactional
    public Map<String,Object> create(Long datasetId,CreateTask request) {
        requireDataset(datasetId,true);if(request==null||request.name()==null||request.name().isBlank()||request.name().trim().length()>160)throw new IllegalArgumentException("任务名称须为 1～160 字");
        if(request.ruleSetId()==null||request.ruleSetId()<1)throw new IllegalArgumentException("请选择已发布规则版本");
        List<Map<String,Object>> rules=versions.rules(datasetId,request.ruleSetId());validateRunnable(rules);
        String scope=request.scanScope()==null?"FULL":request.scanScope().trim().toUpperCase();if(!Set.of("FULL","SAMPLE").contains(scope))throw new IllegalArgumentException("扫描范围仅支持 FULL 或 SAMPLE");
        int timeout=request.timeoutSeconds()==null?120:request.timeoutSeconds();if(timeout<1||timeout>120)throw new IllegalArgumentException("任务超时须在 1～120 秒内");
        String scheduleType=request.scheduleType()==null?"MANUAL":request.scheduleType().trim().toUpperCase();if(!Set.of("MANUAL","INTERVAL").contains(scheduleType))throw new IllegalArgumentException("质量任务调度类型无效");
        int interval=request.intervalMinutes()==null?60:request.intervalMinutes();if("INTERVAL".equals(scheduleType)&&(interval<15||interval>10080))throw new IllegalArgumentException("定时运行间隔须在 15 分钟到 7 天之间");
        int retryLimit=request.retryLimit()==null?0:request.retryLimit();if(retryLimit<0||retryLimit>3)throw new IllegalArgumentException("自动重试次数须在 0～3 次之间");
        var actor=actors.currentActor();Map<String,Object> row=new HashMap<>();row.put("datasetId",datasetId);row.put("ruleSetId",request.ruleSetId());row.put("name",request.name().trim());row.put("scanScope",scope);row.put("timeoutSeconds",timeout);row.put("retryLimit",retryLimit);row.put("scheduleType",scheduleType);row.put("scheduleJson","MANUAL".equals(scheduleType)?null:scheduledConfig(actor,interval));row.put("actor",actor.username());
        if(mapper.insertTask(row)!=1||!(row.get("id") instanceof Number id))throw new IllegalStateException("创建质量任务失败");return mapper.selectTask(id.longValue());
    }

    public Map<String,Object> status(Long taskId,String status) {
        Map<String,Object> task=requireTask(taskId,true);if(status==null||!Set.of("PAUSED","ENABLED").contains(status.trim().toUpperCase()))throw new IllegalArgumentException("质量任务状态无效");String target=status.trim().toUpperCase();
        if(mapper.updateStatus(taskId,target,actors.currentActor().username())!=1)throw new IllegalStateException("任务状态更新失败");return mapper.selectTask(taskId);
    }

    public Map<String,Object> trigger(Long taskId,String traceId) {
        Map<String,Object> task=requireTask(taskId,true);if(!"ENABLED".equals(String.valueOf(task.get("status"))))throw new IllegalArgumentException("已暂停的质量任务不能执行");
        if(!"PUBLISHED".equals(String.valueOf(task.get("ruleSetStatus"))))throw new IllegalArgumentException("任务规则版本未发布");
        poolGate.assertLaneRunnable("QUALITY", mapper.activeGlobalCount());
        long datasetId=number(task.get("datasetId")),setId=number(task.get("ruleSetId"));List<Map<String,Object>> rules=versions.rules(datasetId,setId);validateRunnable(rules);
        BiActor actor=actors.currentActor();Map<String,Object> row=new HashMap<>();row.put("taskId",taskId);row.put("datasetId",datasetId);row.put("ruleSetId",setId);row.put("ruleSetVersion",number(task.get("ruleVersion")));row.put("scanScope",task.get("scanScope"));row.put("idempotencyKey",null);row.put("traceId",bounded(traceId,64));row.put("actor",bounded(actor.username(),64));
        row.put("triggerType","MANUAL");long id=insertQueuedRunWithLock(taskId,row);
        submitRun(id,task,actor);
        return Map.of("runId",id,"status","QUEUED","taskId",taskId);
    }

    @Scheduled(fixedDelayString="${bi.quality.scheduler-delay-ms:60000}")
    public void dispatchScheduledTasks(){
        long now=System.currentTimeMillis();
        for(Map<String,Object> due:mapper.dueScheduledTasks(now)){
            Long taskId=number(due.get("id"));
            try{
                Map<String,Object> schedule=readMap(due.get("scheduleJson"));long expected=number(schedule.get("nextRunEpoch"));int interval=Math.toIntExact(number(schedule.get("intervalMinutes")));
                BiActor reference=json.readValue(json.writeValueAsString(schedule.get("actor")),BiActor.class);
                long next=Math.max(now,expected)+interval*60_000L;
                if(mapper.claimScheduledTask(taskId,expected,next)!=1)continue;
                Map<String,Object> task=mapper.selectTask(taskId);if(task==null||!"ENABLED".equals(String.valueOf(task.get("status"))))continue;
                List<Map<String,Object>> rules=versions.rules(number(task.get("datasetId")),number(task.get("ruleSetId")));validateRunnable(rules);
                enqueueScheduled(task,reference);
            }catch(Exception failure){org.slf4j.LoggerFactory.getLogger(QualityTaskService.class).warn("Scheduled quality run could not be queued for task {}: {}",taskId,safeMessage(failure));}
        }
    }

    private void enqueueScheduled(Map<String,Object> task,BiActor actor){
        poolGate.assertLaneRunnable("QUALITY", mapper.activeGlobalCount());
        Long taskId=number(task.get("id"));long datasetId=number(task.get("datasetId")),setId=number(task.get("ruleSetId"));
        Map<String,Object> row=new HashMap<>();row.put("taskId",taskId);row.put("datasetId",datasetId);row.put("ruleSetId",setId);row.put("ruleSetVersion",number(task.get("ruleVersion")));row.put("scanScope",task.get("scanScope"));row.put("idempotencyKey",null);row.put("traceId","quality-schedule-"+taskId);row.put("actor",bounded(actor.username(),64));row.put("triggerType","SCHEDULED");
        long id=insertQueuedRunWithLock(taskId,row);
        submitRun(id,task,actor);
    }

    public Map<String,Object> cancel(Long taskId,Long runId){
        Map<String,Object> task=requireTask(taskId,false);Map<String,Object> run=mapper.selectRun(runId);
        if(run==null||number(run.get("taskId"))!=taskId)throw new IllegalArgumentException("质量运行记录不属于该任务");
        if(!Set.of("QUEUED","RUNNING").contains(String.valueOf(run.get("status"))))throw new IllegalStateException("只有排队中或运行中的质量任务可以取消");
        if(mapper.cancelRun(taskId,runId)!=1)throw new IllegalStateException("任务状态已变化，请刷新后重试");
        Future<?> future=runFutures.get(runId);if(future!=null)future.cancel(true);ScheduledFuture<?> retry=retryFutures.remove(runId);if(retry!=null)retry.cancel(true);
        Map<String,Object> result=new LinkedHashMap<>(run);result.put("status","CANCELLED");result.put("cancelled",true);result.put("taskName",task.get("name"));return result;
    }

    private void submitRun(long id,Map<String,Object> task,BiActor actor){
        FutureTask<Void> future=new FutureTask<>(()->{try{execute(id,task,actor);}finally{runFutures.remove(id);}return null;});
        runFutures.put(id,future);
        try{executor.execute(future);}catch(RuntimeException rejected){runFutures.remove(id,future);mapper.failQueued(id);}
    }

    private void scheduleAutomaticRetry(long failedRunId,Map<String,Object> task,BiActor actor){
        Map<String,Object> previous=mapper.selectRun(failedRunId);if(previous==null||!"FAILED".equals(String.valueOf(previous.get("status"))))return;
        Map<String,Object> currentTask=mapper.selectTask(number(task.get("id")));
        if(currentTask==null||!"ENABLED".equals(String.valueOf(currentTask.get("status"))))return;
        int completedAttempt=Math.toIntExact(number(previous.get("attemptNo"))),limit=Math.toIntExact(number(currentTask.get("retryLimit")));
        if(completedAttempt<1||completedAttempt>limit)return;
        int nextAttempt=completedAttempt+1;Map<String,Object> row=new HashMap<>();row.put("taskId",currentTask.get("id"));row.put("retryOfRunId",failedRunId);row.put("attemptNo",nextAttempt);row.put("datasetId",currentTask.get("datasetId"));row.put("ruleSetId",currentTask.get("ruleSetId"));row.put("ruleSetVersion",currentTask.get("ruleVersion"));row.put("scanScope",currentTask.get("scanScope"));row.put("traceId",bounded("quality-"+failedRunId+"-attempt-"+nextAttempt,64));row.put("actor",bounded(actor.username(),64));
        try{
            if(mapper.insertRetryRun(row)!=1||!(row.get("id") instanceof Number newId))return;
            long delay=Math.min(300_000L,15_000L<<(completedAttempt-1));long retryRunId=newId.longValue();
            ScheduledFuture<?> future=retryTimer.schedule(()->{retryFutures.remove(retryRunId);submitRun(retryRunId,currentTask,actor);},delay,TimeUnit.MILLISECONDS);
            retryFutures.put(retryRunId,future);
            org.slf4j.LoggerFactory.getLogger(QualityTaskService.class).info("Scheduled bounded retry attempt {} for quality run {}",nextAttempt,failedRunId);
        }catch(org.springframework.dao.DuplicateKeyException duplicate){
            org.slf4j.LoggerFactory.getLogger(QualityTaskService.class).debug("Quality run {} already has a retry attempt",failedRunId);
        }catch(RuntimeException rejected){
            if(row.get("id") instanceof Number newId)mapper.failQueued(newId.longValue());
            org.slf4j.LoggerFactory.getLogger(QualityTaskService.class).warn("Could not schedule quality retry for run {}",failedRunId);
        }
    }

    private static boolean isRetryable(Throwable failure){
        if(Thread.currentThread().isInterrupted())return false;
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()){
            if(cause instanceof BiAccessException||cause instanceof IllegalArgumentException)return false;
            if(cause instanceof SQLTransientException||cause instanceof SQLRecoverableException||cause instanceof SQLNonTransientConnectionException||cause instanceof SocketTimeoutException||cause instanceof ConnectException||cause instanceof TimeoutException)return true;
        }
        return false;
    }

    private String scheduledConfig(BiActor actor,int interval){try{return json.writeValueAsString(Map.of("intervalMinutes",interval,"nextRunEpoch",System.currentTimeMillis()+interval*60_000L,"actor",actor));}catch(Exception failure){throw new IllegalStateException("质量任务调度配置保存失败",failure);}}
    private Map<String,Object> readMap(Object value){if(value==null)return Map.of();try{return json.readValue(String.valueOf(value),Map.class);}catch(Exception failure){throw new IllegalStateException("质量任务调度配置无效");}}

    public List<Map<String,Object>> runs(Long taskId){requireTask(taskId,false);return mapper.listRuns(taskId);}
    public List<Map<String,Object>> results(Long taskId,Long runId){requireTask(taskId,false);boolean owned=mapper.listRuns(taskId).stream().anyMatch(row->number(row.get("id"))==runId);if(!owned)throw new IllegalArgumentException("运行记录不属于该任务");return mapper.listResults(runId);}
    public List<Map<String,Object>> issues(Long datasetId){requireDataset(datasetId,false);return mapper.listIssues(datasetId);}
    public List<Map<String,Object>> trend(Long datasetId,int days){requireDataset(datasetId,false);if(!Set.of(7,30,90).contains(days))throw new IllegalArgumentException("质量趋势范围仅支持 7、30 或 90 天");return mapper.qualityTrend(datasetId,days);}
    public Map<String,Object> score(Long datasetId){requireDataset(datasetId,false);Map<String,Object> run=mapper.latestFullRun(datasetId);Map<String,Object> out=new LinkedHashMap<>();out.put("datasetId",datasetId);out.put("formula","最近一次全量运行的规则通过数 / 已执行规则数 × 100");out.put("basis","FULL");out.put("hasResult",run!=null);if(run==null){out.put("score",null);return out;}long total=number(run.get("rulesTotal")),passed=number(run.get("rulesPassed"));out.put("score",total==0?null:Math.round((double)passed*10000/total)/100.0);out.put("status",run.get("status"));out.put("rowsScanned",run.get("rowsScanned"));out.put("rulesTotal",total);out.put("rulesPassed",passed);out.put("rulesFailed",run.get("rulesFailed"));out.put("issueCount",run.get("issueCount"));out.put("completedAt",run.get("completedAt"));out.put("runId",run.get("id"));out.put("warning","此分数只衡量已执行规则通过率，不替代业务质量验收或新鲜度评分");return out;}
    public List<Map<String,Object>> issueHistory(Long datasetId,Long issueId){requireIssue(datasetId,issueId);return mapper.listIssueHistory(issueId);}
    @Transactional
    public Map<String,Object> claimIssue(Long datasetId,Long issueId){requireIssue(datasetId,issueId);String actor=bounded(actors.currentActor().username(),64);if(mapper.claimIssue(issueId,actor,issueSlaHours)!=1)throw new IllegalArgumentException("问题已被认领或已经关闭");mapper.insertIssueHistory(issueId,actor,"CLAIMED","认领问题处理（SLA "+issueSlaHours+" 小时）");return mapper.listIssues(datasetId).stream().filter(item->number(item.get("id"))==issueId).findFirst().orElseThrow();}
    public Map<String,Object> issueSlaSummary(Long datasetId){requireDataset(datasetId,false);Map<String,Object> out=new LinkedHashMap<>();out.put("datasetId",datasetId);out.put("slaHours",issueSlaHours);out.put("overdueClaimed",mapper.overdueIssueCount(datasetId));return out;}
    /**
     * Marks a claimed issue as fixed-pending. Closure requires a later successful rule PASS
     * on the same dataset/rule via {@link #closeVerifiedIssues}.
     */
    @Transactional
    public Map<String,Object> resolveIssue(Long datasetId,Long issueId,String note){
        requireIssue(datasetId,issueId);
        if(note==null||note.isBlank()||note.length()>1000)throw new IllegalArgumentException("处置说明须为 1～1000 字");
        String actor=bounded(actors.currentActor().username(),64);
        String clean=note.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]","").trim();
        if(clean.isBlank())throw new IllegalArgumentException("处置说明无效");
        if(mapper.markFixedPending(issueId,actor,clean)!=1)
            throw new IllegalArgumentException("只能将本人已认领的问题标记为已修复（待复验）");
        mapper.insertIssueHistory(issueId,actor,"FIXED_PENDING",clean);
        return mapper.listIssues(datasetId).stream().filter(item->number(item.get("id"))==issueId).findFirst().orElseThrow();
    }

    private void execute(long runId,Map<String,Object> task,BiActor actor) {
        if(mapper.startRun(runId)!=1)return;
        try {
            Map<String,Object> latest=mapper.selectTask(number(task.get("id")));
            if(latest==null||!"ENABLED".equals(String.valueOf(latest.get("status")))){mapper.finishRun(failedRun(runId,"任务已暂停，排队中的执行已取消"));return;}
            var current=live.resolve(actor);long datasetId=number(task.get("datasetId"));current.require("bi:dataset:query");
            ActorScope.run(current.actor(),current.capabilities(),()->{
                try{runRules(runId,task,datasetId);}catch(Exception ex){throw new QualityExecutionFailure(ex);}
            });
        } catch(Exception failure) {
            mapper.finishRun(failedRun(runId,safeMessage(failure)));
            if(isRetryable(failure))scheduleAutomaticRetry(runId,task,actor);
        }
    }

    private void runRules(long runId,Map<String,Object> task,long datasetId)throws Exception {
        boolean full="FULL".equals(String.valueOf(task.get("scanScope")));
        Map<String,Object> report=datasetService.qualityReport(datasetId,full,Math.toIntExact(number(task.get("timeoutSeconds"))));List<Map<String,Object>> rules=versions.rules(datasetId,number(task.get("ruleSetId")));
        List<?> columns=report.get("columns") instanceof List<?> list?list:List.of();Map<String,Map<String,Object>> byField=new LinkedHashMap<>();
        for(Object column:columns)if(column instanceof Map<?,?> values){Map<String,Object> mapped=new LinkedHashMap<>();values.forEach((key,value)->mapped.put(String.valueOf(key),value));byField.put(String.valueOf(mapped.get("field")),mapped);}
        long rows=number(report.getOrDefault("scannedRows",report.getOrDefault("sampledRows",0)));int total=0,passed=0,failed=0;long issues=0;
        boolean needsSamples=rules.stream().filter(rule->enabled(rule.get("enabled"))).map(rule->String.valueOf(rule.get("type"))).anyMatch(type->Set.of("RANGE","REGEX","FRESHNESS").contains(type));
        List<Map<String,Object>> sampleRows=needsSamples?sampleRows(datasetId):List.of();
        Long previousRows=null;
        for(Map<String,Object> rule:rules){if(!enabled(rule.get("enabled")))continue;total++;String type=String.valueOf(rule.get("type"));String field=rule.get("field")==null||String.valueOf(rule.get("field")).isBlank()?null:String.valueOf(rule.get("field"));Map<String,Object> stats=field==null?null:byField.get(field);
            if(Set.of("NOT_NULL","UNIQUE","NULL_RATE_MAX").contains(type)&&stats==null)throw new IllegalArgumentException("质量规则字段已失效");
            if(Set.of("RANGE","REGEX","FRESHNESS").contains(type)&&(field==null||sampleRows.isEmpty()&&stats==null))throw new IllegalArgumentException("质量规则字段已失效或无可评估样本");
            long started=System.nanoTime();Map<String,Object> config=asMap(rule.get("config"));boolean pass;long bad;String expected;BigDecimal metric;
            switch(type){
                case "NOT_NULL"->{long nulls=number(stats.get("nullCount"));bad=nulls;pass=bad==0;expected="nullCount=0";metric=BigDecimal.valueOf(bad);}
                case "UNIQUE"->{long duplicates=number(stats.get("duplicateValueCount"));bad=duplicates;pass=bad==0;expected="duplicateValueCount=0";metric=BigDecimal.valueOf(bad);}
                case "NULL_RATE_MAX"->{double nullRate=decimal(stats.get("nullRate")).doubleValue();double max=decimal(config.get("maxRate")).doubleValue();bad=nullRate>max?1:0;pass=bad==0;expected="nullRate <= "+max;metric=BigDecimal.valueOf(nullRate);}
                case "RANGE"->{RuleEval eval=evaluateRange(sampleRows,field,config);bad=eval.bad();pass=bad==0;expected=eval.expected();metric=BigDecimal.valueOf(bad);rows=Math.max(rows,eval.rows());}
                case "REGEX"->{RuleEval eval=evaluateRegex(sampleRows,field,config);bad=eval.bad();pass=bad==0;expected=eval.expected();metric=BigDecimal.valueOf(bad);rows=Math.max(rows,eval.rows());}
                case "FRESHNESS"->{RuleEval eval=evaluateFreshness(sampleRows,field,config);bad=eval.bad();pass=bad==0;expected=eval.expected();metric=BigDecimal.valueOf(eval.metric());rows=Math.max(rows,eval.rows());}
                case "RECONCILIATION"->{if(previousRows==null)previousRows=previousSucceededRows(datasetId,runId);RuleEval eval=evaluateReconciliation(rows,previousRows,config);bad=eval.bad();pass=bad==0;expected=eval.expected();metric=BigDecimal.valueOf(eval.metric());}
                default->throw new IllegalArgumentException("任务包含尚未接入执行器的规则类型: "+type);
            }
            if(pass)passed++;else{failed++;issues++;}Map<String,Object> result=new HashMap<>();result.put("runId",runId);result.put("ruleId",rule.get("id"));result.put("status",pass?"PASS":"FAIL");result.put("rows",rows);result.put("passed",pass?rows:Math.max(0,rows-bad));result.put("failed",bad);result.put("metric",metric);result.put("expected",expected);result.put("summary",pass?"规则通过":"规则未通过");result.put("duration",Math.max(0,(System.nanoTime()-started)/1_000_000));if(mapper.insertResult(result)!=1)throw new IllegalStateException("保存质量规则结果失败");
            if(!pass){
                Map<String,Object> issue=new HashMap<>();
                issue.put("runId",runId);issue.put("ruleId",rule.get("id"));
                issue.put("issueKey",issueKey(runId,number(rule.get("id"))));
                issue.put("severity",String.valueOf(rule.get("severity")));
                issue.put("summary","规则 "+String.valueOf(rule.get("code"))+" 未通过；失败计数 "+bad+"，扫描行数 "+rows+"。未保存源记录样例。");
                if(mapper.insertIssue(issue)!=1||!(issue.get("id") instanceof Number issueId))
                    throw new IllegalStateException("保存质量问题失败");
                mapper.insertIssueHistory(issueId.longValue(),actors.currentActor().username(),"DETECTED","质量规则运行检测到问题");
                try{
                    alerts.notifyIssueDetected(datasetId,number(rule.get("id")),issueId.longValue(),
                            String.valueOf(rule.get("severity")),String.valueOf(rule.get("code")));
                }catch(Exception alertFailure){
                    org.slf4j.LoggerFactory.getLogger(QualityTaskService.class)
                            .warn("Quality alert notify skipped for issue {}",issueId,alertFailure);
                }
            } else {
                closeVerifiedIssues(datasetId,number(rule.get("id")),runId);
            }
        }
        if(total==0)throw new IllegalArgumentException("规则版本没有启用规则");Map<String,Object> finish=new HashMap<>();finish.put("id",runId);finish.put("status",failed==0?"SUCCEEDED":"FAILED");finish.put("rows",rows);finish.put("total",total);finish.put("passed",passed);finish.put("failed",failed);finish.put("issues",issues);finish.put("failureCode",failed==0?null:"QUALITY_RULE_FAILED");finish.put("failureSummary",failed==0?null:"存在未通过的质量规则");if(mapper.finishRun(finish)!=1)throw new IllegalStateException("保存质量运行状态失败");
    }

    /** Close FIXED_PENDING issues when the same rule PASSes on a later run. */
    private void closeVerifiedIssues(long datasetId,long ruleId,long runId){
        String result=mapper.latestResultStatus(runId,ruleId);
        if(!"PASS".equals(result))return;
        String actor=bounded(actors.currentActor().username(),64);
        for(Map<String,Object> pending:alertMapper.listFixedPending(datasetId,ruleId)){
            long issueId=number(pending.get("id"));
            if(mapper.closeAfterVerify(issueId,actor,runId)==1)
                mapper.insertIssueHistory(issueId,actor,"VERIFIED","复验运行 "+runId+" 规则通过，问题已关闭");
        }
    }

    private List<Map<String,Object>> sampleRows(long datasetId)throws Exception{
        Map<String,Object> preview=datasetService.preview(datasetId,VALUE_RULE_SAMPLE_LIMIT);
        Object rawRows=preview.get("rows");
        if(!(rawRows instanceof List<?> rows))return List.of();
        List<Map<String,Object>> records=new ArrayList<>();
        for(Object row:rows)if(row instanceof Map<?,?> map){Map<String,Object> record=new LinkedHashMap<>();map.forEach((k,v)->record.put(String.valueOf(k),v));records.add(record);}
        return records;
    }

    private Long previousSucceededRows(long datasetId,long currentRunId){
        Map<String,Object> previous=mapper.latestFullRun(datasetId);
        if(previous==null)return null;
        Long previousId=number(previous.get("id"));
        if(previousId==currentRunId)return null;
        if(!"SUCCEEDED".equals(String.valueOf(previous.get("status"))))return null;
        return number(previous.get("rowsScanned"));
    }

    private static RuleEval evaluateRange(List<Map<String,Object>> rows,String field,Map<String,Object> config){
        Double min=config.containsKey("min")?decimal(config.get("min")).doubleValue():null;
        Double max=config.containsKey("max")?decimal(config.get("max")).doubleValue():null;
        long bad=0,checked=0;
        for(Map<String,Object> row:rows){
            Object value=row.get(field);if(value==null)continue;
            double number;try{number=new BigDecimal(String.valueOf(value)).doubleValue();}catch(Exception ignored){bad++;checked++;continue;}
            checked++;
            if(min!=null&&number<min)bad++;
            else if(max!=null&&number>max)bad++;
        }
        if(checked==0)throw new IllegalArgumentException("RANGE 规则没有可评估的数值样本");
        String expected=(min==null?"":"min>="+min)+(min!=null&&max!=null?" / ":"")+(max==null?"":"max<="+max);
        return new RuleEval(bad,checked,expected,bad);
    }

    private static RuleEval evaluateRegex(List<Map<String,Object>> rows,String field,Map<String,Object> config){
        Pattern pattern=Pattern.compile(String.valueOf(config.get("pattern")));
        long bad=0,checked=0;
        for(Map<String,Object> row:rows){
            Object value=row.get(field);if(value==null)continue;
            checked++;
            if(!pattern.matcher(String.valueOf(value)).find())bad++;
        }
        if(checked==0)throw new IllegalArgumentException("REGEX 规则没有可评估的非空样本");
        return new RuleEval(bad,checked,"matches "+config.get("pattern"),bad);
    }

    private static RuleEval evaluateFreshness(List<Map<String,Object>> rows,String field,Map<String,Object> config){
        if(field==null)throw new IllegalArgumentException("FRESHNESS 需要时间字段");
        long maxAge=decimal(config.get("maxAgeSeconds")).longValue();
        Instant newest=null;
        for(Map<String,Object> row:rows){
            Instant instant=asInstant(row.get(field));
            if(instant==null)continue;
            if(newest==null||instant.isAfter(newest))newest=instant;
        }
        if(newest==null)throw new IllegalArgumentException("FRESHNESS 规则没有可评估的时间样本");
        long ageSeconds=Math.max(0,Instant.now().getEpochSecond()-newest.getEpochSecond());
        long bad=ageSeconds>maxAge?1:0;
        return new RuleEval(bad,rows.size(),"maxAgeSeconds<="+maxAge,ageSeconds);
    }

    private static RuleEval evaluateReconciliation(long currentRows,Long previousRows,Map<String,Object> config){
        double tolerance=decimal(config.get("tolerance")).doubleValue();
        if(previousRows==null)return new RuleEval(0,currentRows,"no baseline run",0);
        double delta=Math.abs(currentRows-previousRows);
        long bad=delta>tolerance?1:0;
        return new RuleEval(bad,currentRows,"|rows-previous| <= "+tolerance,delta);
    }

    private static Instant asInstant(Object value){
        if(value==null)return null;
        if(value instanceof Instant instant)return instant;
        if(value instanceof OffsetDateTime time)return time.toInstant();
        if(value instanceof LocalDateTime time)return time.toInstant(ZoneOffset.UTC);
        if(value instanceof LocalDate date)return date.atStartOfDay().toInstant(ZoneOffset.UTC);
        if(value instanceof java.sql.Timestamp timestamp)return timestamp.toInstant();
        if(value instanceof java.sql.Date date)return date.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        if(value instanceof java.util.Date date)return date.toInstant();
        if(value instanceof Number number)return Instant.ofEpochMilli(number.longValue());
        String text=String.valueOf(value).trim();
        if(text.isEmpty())return null;
        try{return Instant.parse(text);}catch(Exception ignored){}
        try{return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);}catch(Exception ignored){}
        try{return LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC);}catch(Exception ignored){}
        return null;
    }

    private record RuleEval(long bad,long rows,String expected,double metric){}

    private long insertQueuedRunWithLock(Long taskId,Map<String,Object> row){String lock="dp-quality-task:"+taskId;try(Connection connection=platformDataSource.getConnection()){
        if(!acquireLock(connection,lock))throw new IllegalArgumentException("该质量任务正在触发，请稍后重试");
        try{if(mapper.countActiveRuns(taskId)>0)throw new IllegalArgumentException("该质量任务已有运行中的执行记录");if(mapper.insertRun(row)!=1||!(row.get("id") instanceof Number id))throw new IllegalStateException("创建质量运行记录失败");return id.longValue();}
        finally{releaseLock(connection,lock);}
    }catch(SQLException failure){throw new IllegalStateException("质量任务触发锁不可用",failure);}}
    private static boolean acquireLock(Connection connection,String lock)throws SQLException{try(var statement=connection.prepareStatement("SELECT GET_LOCK(?,0)")){statement.setString(1,lock);try(ResultSet rows=statement.executeQuery()){return rows.next()&&rows.getInt(1)==1;}}}
    private static void releaseLock(Connection connection,String lock){try(var statement=connection.prepareStatement("SELECT RELEASE_LOCK(?)")){statement.setString(1,lock);try(ResultSet ignored=statement.executeQuery()){}}catch(SQLException ignored){}}

    private Map<String,Object> failedRun(long id,String message){Map<String,Object> row=new HashMap<>();row.put("id",id);row.put("status","FAILED");row.put("rows",0);row.put("total",0);row.put("passed",0);row.put("failed",0);row.put("issues",0);row.put("failureCode","QUALITY_EXECUTION_FAILED");row.put("failureSummary",message);return row;}
    private Map<String,Object> requireTask(Long id,boolean query){if(id==null||id<1)throw new IllegalArgumentException("质量任务编号无效");Map<String,Object> task=mapper.selectTask(id);if(task==null)throw new IllegalArgumentException("质量任务不存在");requireDataset(number(task.get("datasetId")),query);return task;}
    private void requireIssue(Long datasetId,Long issueId){requireDataset(datasetId,false);Map<String,Object> issue=mapper.selectIssue(issueId);if(issue==null||number(issue.get("datasetId"))!=datasetId)throw new IllegalArgumentException("质量问题不存在");}
    private void requireDataset(Long id,boolean edit){capabilities.require(edit?"bi:datasource:model":"bi:dataset:query");resources.require("dataset",id,edit?"view":"query");var dataset=datasets.selectById(id);if(dataset==null)throw new IllegalArgumentException("数据集不存在");if(edit&&dataset.getGroupId()!=null&&dataset.getGroupId()!=0)resources.require("dataset-group",dataset.getGroupId(),"edit");}
    private static void validateRunnable(List<Map<String,Object>> rules){if(rules==null||rules.stream().noneMatch(rule->enabled(rule.get("enabled"))))throw new IllegalArgumentException("规则版本没有启用规则");List<String> unsupported=rules.stream().filter(rule->enabled(rule.get("enabled"))).map(rule->String.valueOf(rule.get("type"))).filter(type->!EXECUTABLE_RULES.contains(type)).distinct().toList();if(!unsupported.isEmpty())throw new IllegalArgumentException("以下规则类型尚未接入执行器: "+String.join(", ",unsupported));}
    private static Map<String,Object> asMap(Object value){if(value instanceof Map<?,?> map){Map<String,Object> result=new HashMap<>();map.forEach((key,item)->result.put(String.valueOf(key),item));return result;}return Map.of();}
    private static BigDecimal decimal(Object value){try{return value instanceof BigDecimal number?number:new BigDecimal(String.valueOf(value));}catch(Exception error){throw new IllegalArgumentException("质量指标值无效");}}
    private static long number(Object value){return value instanceof Number number?number.longValue():Long.parseLong(String.valueOf(value));}
    private static boolean enabled(Object value){return Boolean.TRUE.equals(value)||(value instanceof Number number&&number.intValue()==1)||"true".equalsIgnoreCase(String.valueOf(value));}
    private static String bounded(String value,int max){return value==null?"":value.substring(0,Math.min(max,value.length()));}
    private static String issueKey(long runId,long ruleId){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((runId+":"+ruleId).getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalStateException("质量问题编码生成失败",error);}}
    private static String safeMessage(Exception error){Throwable cause=error instanceof QualityExecutionFailure&&error.getCause()!=null?error.getCause():error;String message=cause.getMessage();if(message==null||message.isBlank())return "质量检查执行失败";return message.substring(0,Math.min(240,message.length()));}
    private static final class QualityExecutionFailure extends RuntimeException{QualityExecutionFailure(Throwable cause){super(cause);}}
}
