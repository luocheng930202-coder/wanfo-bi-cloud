package com.wanfo.bi.service;

import com.wanfo.bi.mapper.BiReportSubscriptionMapper;
import com.wanfo.bi.security.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class ReportSubscriptionService {
    public record Create(String sourceType,Long sourceId,String panelKey,String format,String name,List<String> recipients,int intervalMinutes) {}
    public record Update(List<String> recipients,int intervalMinutes,boolean enabled) {}
    private static final Pattern EMAIL=Pattern.compile("^[^\\s@,;]{1,64}@[^\\s@,;]{1,190}$");
    private final BiReportSubscriptionMapper mapper;
    private final ExportJobService exports;
    private final CurrentActorProvider actors;
    private final CapabilityProvider caps;
    private final ObjectMapper json;
    private final JavaMailSender mail;
    private final boolean enabled;
    private final Set<String> allowedDomains;

    public ReportSubscriptionService(BiReportSubscriptionMapper mapper,ExportJobService exports,CurrentActorProvider actors,
            CapabilityProvider caps,ObjectMapper json,ObjectProvider<JavaMailSender> mail,
            @Value("${bi.report-subscription.enabled:false}") boolean enabled,
            @Value("${bi.report-subscription.allowed-domains:}") String allowedDomains) {
        this.mapper=mapper;this.exports=exports;this.actors=actors;this.caps=caps;this.json=json;this.mail=mail.getIfAvailable();this.enabled=enabled;
        this.allowedDomains=Arrays.stream(allowedDomains.split(",")).map(String::trim).map(s->s.replaceFirst("^@","" ).toLowerCase(Locale.ROOT)).filter(s->!s.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public List<Map<String,Object>> list(){caps.require("bi:subscription:list");return mapper.list(actors.currentActor().userId());}
    public Map<String,Object> create(Create request){
        caps.require("bi:subscription:add");
        if(!enabled||mail==null||allowedDomains.isEmpty())throw new IllegalArgumentException("报表订阅尚未启用，请先配置订阅开关、SMTP 邮件服务和收件人域名白名单");
        if(request==null||request.sourceId()==null||request.sourceId()<1)throw new IllegalArgumentException("请选择已保存图表或已发布看板");
        String type=clean(request.sourceType(),16).toLowerCase(Locale.ROOT),format=clean(request.format(),8).toLowerCase(Locale.ROOT);
        if(!Set.of("chart","dashboard").contains(type)||!Set.of("csv","xlsx","pdf").contains(format))throw new IllegalArgumentException("订阅来源或格式无效");
        String panel=request.panelKey()==null?"":request.panelKey().trim();if(!panel.isEmpty()&&!panel.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("组件标识无效");
        String name=clean(request.name()==null?"定时报表":request.name(),200);if(name.isBlank())throw new IllegalArgumentException("订阅名称不能为空");
        List<String> recipients=recipients(request.recipients());int interval=request.intervalMinutes();if(interval<15||interval>10080)throw new IllegalArgumentException("执行间隔需为 15～10080 分钟");
        BiActor actor=actors.currentActor();exports.validateSubscription(actor,new ExportJobService.Submit(type,request.sourceId(),panel.isBlank()?null:panel,null,format,name,Map.of()));Map<String,Object> row=new LinkedHashMap<>();row.put("owner",actor.userId());row.put("actorJson",json.writeValueAsString(actor));row.put("sourceType",type);row.put("sourceId",request.sourceId());row.put("panelKey",panel);row.put("format",format);row.put("name",name);row.put("recipientsJson",json.writeValueAsString(recipients));row.put("intervalMinutes",interval);row.put("actorName",actor.username());mapper.insert(row);
        return Map.of("id",row.get("id"),"status","waiting");
    }
    public Map<String,Object> update(Long id,Update request){
        caps.require("bi:subscription:edit");var existing=owned(id);if(request==null)throw new IllegalArgumentException("订阅内容无效");
        if(request.intervalMinutes()<15||request.intervalMinutes()>10080)throw new IllegalArgumentException("执行间隔需为 15～10080 分钟");
        if(request.enabled()&&(!enabled||mail==null||allowedDomains.isEmpty()))throw new IllegalArgumentException("报表订阅尚未启用，请先配置 SMTP 邮件服务和收件人域名白名单");
        Object saved=existing.get("recipients_json");String recipientsJson=request.recipients()==null?String.valueOf(saved):json.writeValueAsString(recipients(request.recipients()));
        if(request.enabled()&&request.recipients()==null){@SuppressWarnings("unchecked") List<String> savedRecipients=(List<String>)json.readValue(recipientsJson,List.class);recipients(savedRecipients);}
        Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("owner",actors.currentActor().userId());row.put("recipientsJson",recipientsJson);row.put("intervalMinutes",request.intervalMinutes());row.put("enabled",request.enabled()?1:0);mapper.update(row);return Map.of("updated",true);
    }
    public Map<String,Object> delete(Long id){caps.require("bi:subscription:remove");owned(id);mapper.delete(id,actors.currentActor().userId());return Map.of("deleted",true);}
    public List<Map<String,Object>> history(Long id){caps.require("bi:subscription:list");owned(id);return mapper.history(id,actors.currentActor().userId());}
    private Map<String,Object> owned(Long id){Map<String,Object> row=mapper.owned(id,actors.currentActor().userId());if(row==null)throw new BiAccessException(404,"订阅不存在或无权访问");return row;}

    @Scheduled(fixedDelayString="${bi.report-subscription.poll-ms:60000}",initialDelayString="${bi.report-subscription.poll-ms:60000}")
    public void deliverDue(){if(!enabled||mail==null)return;for(Long id:mapper.dueIds()){if(mapper.claim(id)!=1)continue;deliver(id);}}
    private void deliver(Long id){
        Map<String,Object> sub=mapper.get(id);if(sub==null)return;
        LocalDateTime started=LocalDateTime.now();int attempt=((Number)sub.getOrDefault("failure_count",0)).intValue()+1;String status="succeeded",message="";int rows=0,recipientCount=0;LocalDateTime next;
        try{
            BiActor actor=json.readValue(String.valueOf(sub.get("actor_json")),BiActor.class);
            @SuppressWarnings("unchecked") List<String> to=(List<String>)json.readValue(String.valueOf(sub.get("recipients_json")),List.class);to=recipients(to);recipientCount=to.size();
            Long sourceId=((Number)sub.get("source_id")).longValue();String panel=String.valueOf(sub.getOrDefault("panel_key",""));String type=String.valueOf(sub.get("source_type"));String format=String.valueOf(sub.get("format"));String name=String.valueOf(sub.get("name"));
            ExportJobService.File file=exports.generateForSubscription(actor,new ExportJobService.Submit(type,sourceId,panel.isBlank()?null:panel,null,format,name,Map.of()));
            MimeMessage mime=mail.createMimeMessage();MimeMessageHelper helper=new MimeMessageHelper(mime,true,"UTF-8");helper.setTo(to.toArray(String[]::new));helper.setSubject("报表订阅："+name);helper.setText("附件为本次按订阅计划生成的报表。系统已按当前有效身份和权限重新查询数据。",false);helper.addAttachment(file.name(),new org.springframework.core.io.ByteArrayResource(file.bytes()));mail.send(mime);
            rows=file.rowCount();int interval=((Number)sub.get("interval_minutes")).intValue();next=LocalDateTime.now().plusMinutes(interval);
        }catch(Exception ex){
            status="failed";message=ex instanceof IllegalArgumentException?clean(ex.getMessage(),500):"生成或发送失败，请检查数据权限、数据源和 SMTP 配置";
            int failures=attempt;boolean keep=failures<3;next=LocalDateTime.now().plusMinutes(failures==1?1:5);Map<String,Object> done=new LinkedHashMap<>();done.put("id",id);done.put("status",status);done.put("message",message);done.put("failureCount",failures);done.put("enabled",keep?1:0);done.put("nextRun",next);mapper.complete(done);audit(id,sub,started,status,attempt,rows,recipientCount,message);return;
        }
        Map<String,Object> done=new LinkedHashMap<>();done.put("id",id);done.put("status",status);done.put("message",message);done.put("failureCount",0);done.put("enabled",1);done.put("nextRun",next);mapper.complete(done);audit(id,sub,started,status,attempt,rows,recipientCount,message);
    }
    private void audit(Long id,Map<String,Object> sub,LocalDateTime started,String status,int attempt,int rows,int recipients,String message){Map<String,Object> row=new LinkedHashMap<>();row.put("subscriptionId",id);row.put("owner",sub.get("owner_principal"));row.put("startedAt",started);row.put("status",status);row.put("attempt",attempt);row.put("rowCount",rows);row.put("recipientCount",recipients);row.put("message",clean(message,500));mapper.audit(row);}
    private List<String> recipients(List<String> raw){if(raw==null||raw.isEmpty()||raw.size()>10)throw new IllegalArgumentException("收件人需为 1～10 个邮箱地址");LinkedHashSet<String> result=new LinkedHashSet<>();for(String item:raw){String email=item==null?"":item.trim();if(!EMAIL.matcher(email).matches())throw new IllegalArgumentException("收件人邮箱格式无效");String domain=email.substring(email.lastIndexOf('@')+1).toLowerCase(Locale.ROOT);if(!allowedDomains.contains(domain))throw new IllegalArgumentException("收件人邮箱域名不在管理员配置的白名单中");result.add(email);}return List.copyOf(result);}
    private static String clean(String text,int max){String value=text==null?"":text.trim();return value.length()>max?value.substring(0,max):value;}
}
