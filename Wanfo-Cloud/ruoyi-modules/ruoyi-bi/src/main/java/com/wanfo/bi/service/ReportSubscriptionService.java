package com.wanfo.bi.service;

import com.wanfo.bi.mapper.BiReportSubscriptionMapper;
import com.wanfo.bi.security.*;
import java.time.*;
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
    public record Create(String sourceType,Long sourceId,String panelKey,String format,String name,List<String> recipients,int intervalMinutes,List<String> channels,SubscriptionSchedule schedule,String templateTitle,String templateBody) {
        public Create(String sourceType,Long sourceId,String panelKey,String format,String name,List<String> recipients,int intervalMinutes){this(sourceType,sourceId,panelKey,format,name,recipients,intervalMinutes,List.of("email"),null,null,null);}
    }
    public record Update(List<String> recipients,int intervalMinutes,boolean enabled,List<String> channels,SubscriptionSchedule schedule,String templateTitle,String templateBody) {
        public Update(List<String> recipients,int intervalMinutes,boolean enabled){this(recipients,intervalMinutes,enabled,null,null,null,null);}
    }
    private static final Pattern EMAIL=Pattern.compile("^[^\\s@,;]{1,64}@[^\\s@,;]{1,190}$");
    private static final Set<String> CHANNELS=Set.of("email","inbox","wecom");
    private static final Pattern TOKEN=Pattern.compile("\\{\\{([A-Za-z]+)}}");
    private static final Set<String> TOKENS=Set.of("reportName","status","rowCount","startedAt");
    private final BiReportSubscriptionMapper mapper;
    private final ExportJobService exports;
    private final CurrentActorProvider actors;
    private final CapabilityProvider caps;
    private final ObjectMapper json;
    private final JavaMailSender mail;
    private final boolean enabled;
    private final Set<String> allowedDomains;
    private final WeComWebhookSender wecom;

    public ReportSubscriptionService(BiReportSubscriptionMapper mapper,ExportJobService exports,CurrentActorProvider actors,
            CapabilityProvider caps,ObjectMapper json,ObjectProvider<JavaMailSender> mail,
            @Value("${bi.report-subscription.enabled:false}") boolean enabled,
            @Value("${bi.report-subscription.allowed-domains:}") String allowedDomains,
            @Value("${bi.report-subscription.wecom.webhook-url:}") String wecomWebhook,
            @Value("${bi.report-subscription.wecom.allowed-hosts:}") String wecomAllowedHosts) {
        this.mapper=mapper;this.exports=exports;this.actors=actors;this.caps=caps;this.json=json;this.mail=mail.getIfAvailable();this.enabled=enabled;
        this.allowedDomains=Arrays.stream(allowedDomains.split(",")).map(String::trim).map(s->s.replaceFirst("^@","").toLowerCase(Locale.ROOT)).filter(s->!s.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.wecom=new WeComWebhookSender(wecomWebhook,wecomAllowedHosts,json);
    }
    public List<Map<String,Object>> list(){caps.require("bi:subscription:list");List<Map<String,Object>> rows=mapper.list(actors.currentActor().userId());for(var row:rows){row.put("channels",readList(row.get("channels"),List.of("email")));if(row.get("schedule")!=null)try{row.put("schedule",json.readValue(String.valueOf(row.get("schedule")),SubscriptionSchedule.class));}catch(Exception ignored){row.put("schedule",null);}}return rows;}
    public Map<String,Object> create(Create request){
        caps.require("bi:subscription:add");if(!enabled)throw new IllegalArgumentException("报表订阅尚未启用，请联系管理员");
        if(request==null||request.sourceId()==null||request.sourceId()<1)throw new IllegalArgumentException("请选择已保存图表或已发布看板");
        String type=clean(request.sourceType(),16).toLowerCase(Locale.ROOT),format=clean(request.format(),8).toLowerCase(Locale.ROOT);
        if(!Set.of("chart","dashboard").contains(type)||!Set.of("csv","xlsx","pdf").contains(format))throw new IllegalArgumentException("订阅来源或格式无效");
        String panel=request.panelKey()==null?"":request.panelKey().trim();if(!panel.isEmpty()&&!panel.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("组件标识无效");
        String name=clean(request.name()==null?"定时报表":request.name(),200);if(name.isBlank())throw new IllegalArgumentException("订阅名称不能为空");
        List<String> channels=channels(request.channels()==null?List.of("email"):request.channels());validateChannels(channels);List<String> recipients=channels.contains("email")?recipients(request.recipients()):List.of();
        SubscriptionSchedule schedule=request.schedule()==null?new SubscriptionSchedule("interval",request.intervalMinutes(),"",List.of(),"UTC"):request.schedule();
        int interval="interval".equals(schedule.kind())?schedule.intervalMinutes():Math.max(15,request.intervalMinutes());
        String templateTitle=template(request.templateTitle()==null?"报表订阅：{{reportName}}":request.templateTitle(),200);
        String templateBody=template(request.templateBody()==null?"报表 {{reportName}} 已生成，数据行数：{{rowCount}}。":request.templateBody(),1000);
        BiActor actor=actors.currentActor();exports.validateSubscription(actor,new ExportJobService.Submit(type,request.sourceId(),panel.isBlank()?null:panel,null,format,name,Map.of()));
        Instant next=schedule.nextAfter(Instant.now());Map<String,Object> row=new LinkedHashMap<>();row.put("owner",actor.userId());row.put("actorJson",json.writeValueAsString(actor));row.put("sourceType",type);row.put("sourceId",request.sourceId());row.put("panelKey",panel);row.put("format",format);row.put("name",name);row.put("recipientsJson",json.writeValueAsString(recipients));row.put("channelsJson",json.writeValueAsString(channels));row.put("scheduleJson",json.writeValueAsString(schedule));row.put("nextRunUtc",SubscriptionSchedule.utc(next));row.put("templateTitle",templateTitle);row.put("templateBody",templateBody);row.put("intervalMinutes",interval);row.put("actorName",actor.username());mapper.insert(row);
        return Map.of("id",row.get("id"),"status","waiting","nextRun",row.get("nextRunUtc"));
    }
    public Map<String,Object> update(Long id,Update request){
        caps.require("bi:subscription:edit");Map<String,Object> existing=owned(id);if(request==null)throw new IllegalArgumentException("订阅内容无效");
        if(request.enabled()&&!enabled)throw new IllegalArgumentException("报表订阅尚未启用，请联系管理员");
        List<String> channels=request.channels()==null?readList(existing.get("channels_json"),List.of("email")):channels(request.channels());if(request.enabled())validateChannels(channels);
        String recipientsJson;
        if(request.recipients()!=null)recipientsJson=json.writeValueAsString(channels.contains("email")?recipients(request.recipients()):List.of());
        else if(!channels.contains("email"))recipientsJson=json.writeValueAsString(List.of());
        else recipientsJson=String.valueOf(existing.get("recipients_json"));
        if(channels.contains("email")){@SuppressWarnings("unchecked") List<String> addresses=(List<String>)json.readValue(recipientsJson,List.class);recipients(addresses);}
        SubscriptionSchedule schedule=request.schedule()==null?readSchedule(existing.get("schedule_json"),request.intervalMinutes()):request.schedule();int interval="interval".equals(schedule.kind())?schedule.intervalMinutes():Math.max(15,request.intervalMinutes());
        String title=template(request.templateTitle()==null?defaultValue(existing.get("template_title"),"报表订阅：{{reportName}}"):request.templateTitle(),200),body=template(request.templateBody()==null?defaultValue(existing.get("template_body"),"报表 {{reportName}} 已生成，数据行数：{{rowCount}}。"):request.templateBody(),1000);
        Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("owner",actors.currentActor().userId());row.put("recipientsJson",recipientsJson);row.put("channelsJson",json.writeValueAsString(channels));row.put("intervalMinutes",interval);row.put("scheduleJson",json.writeValueAsString(schedule));row.put("nextRunUtc",SubscriptionSchedule.utc(schedule.nextAfter(Instant.now())));row.put("templateTitle",title);row.put("templateBody",body);row.put("enabled",request.enabled()?1:0);mapper.update(row);return Map.of("updated",true);
    }
    public Map<String,Object> delete(Long id){caps.require("bi:subscription:remove");owned(id);mapper.delete(id,actors.currentActor().userId());return Map.of("deleted",true);}
    public List<Map<String,Object>> history(Long id){caps.require("bi:subscription:list");owned(id);return mapper.history(id,actors.currentActor().userId());}
    public List<Map<String,Object>> notifications(){return mapper.notifications(actors.currentActor().userId());}
    public Map<String,Object> markNotificationRead(Long id){mapper.markNotificationRead(id,actors.currentActor().userId());return Map.of("updated",true);}
    public List<Map<String,Object>> globalAudit(){caps.require("bi:resource:admin");return mapper.globalAudit();}
    private Map<String,Object> owned(Long id){Map<String,Object> row=mapper.owned(id,actors.currentActor().userId());if(row==null)throw new BiAccessException(404,"订阅不存在或无权访问");return row;}

    @Scheduled(fixedDelayString="${bi.report-subscription.poll-ms:60000}",initialDelayString="${bi.report-subscription.poll-ms:60000}")
    public void deliverDue(){if(!enabled)return;for(Long id:mapper.dueIds()){if(mapper.claim(id)!=1)continue;deliver(id);}}
    private void deliver(Long id){
        Map<String,Object> sub=mapper.get(id);if(sub==null)return;LocalDateTime started=LocalDateTime.now();Instant startedInstant=Instant.now();int attempt=((Number)sub.getOrDefault("failure_count",0)).intValue()+1;String status="succeeded",message="";int rows=0,recipientCount=0;LocalDateTime next=null,nextUtc=null;List<String> activeChannels=readList(sub.get("channels_json"),List.of("email"));
        String reportName=String.valueOf(sub.get("name"));
        try{
            BiActor actor=json.readValue(String.valueOf(sub.get("actor_json")),BiActor.class);@SuppressWarnings("unchecked") List<String> to=(List<String>)json.readValue(String.valueOf(sub.get("recipients_json")),List.class);if(activeChannels.contains("email")){to=recipients(to);recipientCount=to.size();}
            Long sourceId=((Number)sub.get("source_id")).longValue();String panel=String.valueOf(sub.getOrDefault("panel_key","")),type=String.valueOf(sub.get("source_type")),format=String.valueOf(sub.get("format"));
            ExportJobService.File file=exports.generateForSubscription(actor,new ExportJobService.Submit(type,sourceId,panel.isBlank()?null:panel,null,format,reportName,Map.of()));rows=file.rowCount();
            Map<String,String> values=Map.of("reportName",reportName,"status","已生成","rowCount",String.valueOf(rows),"startedAt",started.toString());
            String title=render(String.valueOf(sub.getOrDefault("template_title","报表订阅：{{reportName}}")),values),body=render(String.valueOf(sub.getOrDefault("template_body","报表 {{reportName}} 已生成，数据行数：{{rowCount}}。")),values);
            if(activeChannels.contains("email")){if(mail==null||allowedDomains.isEmpty())throw new IllegalArgumentException("邮件通道未配置，请联系管理员");MimeMessage mime=mail.createMimeMessage();MimeMessageHelper helper=new MimeMessageHelper(mime,true,"UTF-8");helper.setTo(to.toArray(String[]::new));helper.setSubject(title);helper.setText(body+"\n系统已按当前有效身份和权限重新查询数据。",false);helper.addAttachment(file.name(),new org.springframework.core.io.ByteArrayResource(file.bytes()));mail.send(mime);}
            if(activeChannels.contains("inbox"))addInbox(sub,title,body);
            if(activeChannels.contains("wecom"))wecom.send("**"+markdown(title)+"**\n"+markdown(body));
            SubscriptionSchedule schedule=readSchedule(sub.get("schedule_json"),((Number)sub.get("interval_minutes")).intValue());Instant when=schedule.nextAfter(Instant.now());if(sub.get("schedule_json")==null)next=LocalDateTime.now().plusMinutes(schedule.intervalMinutes());else nextUtc=SubscriptionSchedule.utc(when);
        }catch(Exception ex){
            status="failed";message=ex instanceof IllegalArgumentException?clean(ex.getMessage(),500):"生成或发送失败，请检查数据权限、数据源和通知通道配置";
            if(activeChannels.contains("inbox"))try{addInbox(sub,"报表订阅失败："+reportName,message);}catch(Exception ignored){}
            int failures=attempt;boolean keep=failures<3;Instant retry=Instant.now().plusSeconds(failures==1?60:300);next=LocalDateTime.now().plusMinutes(failures==1?1:5);nextUtc=SubscriptionSchedule.utc(retry);Map<String,Object> done=new LinkedHashMap<>();done.put("id",id);done.put("status",status);done.put("message",message);done.put("failureCount",failures);done.put("enabled",keep?1:0);done.put("nextRun",next);done.put("nextRunUtc",nextUtc);mapper.complete(done);audit(id,sub,started,status,attempt,rows,recipientCount,message);return;
        }
        Map<String,Object> done=new LinkedHashMap<>();done.put("id",id);done.put("status",status);done.put("message",message);done.put("failureCount",0);done.put("enabled",1);done.put("nextRun",next);done.put("nextRunUtc",nextUtc);mapper.complete(done);audit(id,sub,started,status,attempt,rows,recipientCount,message);
    }
    private void addInbox(Map<String,Object> sub,String title,String body){Map<String,Object> row=new LinkedHashMap<>();row.put("userId",((Number)sub.get("owner_principal")).longValue());row.put("subscriptionId",((Number)sub.get("id")).longValue());row.put("title",clean(title,200));row.put("body",clean(body,1000));mapper.addNotification(row);}
    private void audit(Long id,Map<String,Object> sub,LocalDateTime started,String status,int attempt,int rows,int recipients,String message){Map<String,Object> row=new LinkedHashMap<>();row.put("subscriptionId",id);row.put("subscriptionName",sub.get("name"));row.put("sourceType",sub.get("source_type"));row.put("sourceId",sub.get("source_id"));row.put("owner",sub.get("owner_principal"));row.put("startedAt",started);row.put("status",status);row.put("attempt",attempt);row.put("rowCount",rows);row.put("recipientCount",recipients);row.put("message",clean(message,500));mapper.audit(row);}
    private List<String> recipients(List<String> raw){if(raw==null||raw.isEmpty()||raw.size()>10)throw new IllegalArgumentException("邮件收件人需为 1～10 个邮箱地址");LinkedHashSet<String> result=new LinkedHashSet<>();for(String item:raw){String email=item==null?"":item.trim();if(!EMAIL.matcher(email).matches())throw new IllegalArgumentException("收件人邮箱格式无效");String domain=email.substring(email.lastIndexOf('@')+1).toLowerCase(Locale.ROOT);if(!allowedDomains.contains(domain))throw new IllegalArgumentException("收件人邮箱域名不在管理员配置的白名单中");result.add(email);}return List.copyOf(result);}
    private List<String> channels(List<String> raw){if(raw==null||raw.isEmpty()||raw.size()>3||raw.stream().anyMatch(c->c==null||!CHANNELS.contains(c)))throw new IllegalArgumentException("请选择邮件、站内通知或企业微信通道");return List.copyOf(new LinkedHashSet<>(raw));}
    private void validateChannels(List<String> value){if(value.contains("email")&&(mail==null||allowedDomains.isEmpty()))throw new IllegalArgumentException("邮件通道尚未配置 SMTP 和收件人域名白名单");if(value.contains("wecom")&&!wecom.configured())throw new IllegalArgumentException("企业微信机器人尚未由管理员配置");}
    private String template(String raw,int max){String value=raw==null?"":raw.trim();if(value.isBlank()||value.length()>max||value.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))throw new IllegalArgumentException("通知模板不能为空、长度超限或含控制字符");var matcher=TOKEN.matcher(value);while(matcher.find())if(!TOKENS.contains(matcher.group(1)))throw new IllegalArgumentException("模板仅支持 reportName、status、rowCount、startedAt 变量");return value;}
    private static String render(String text,Map<String,String> values){String out=text;for(var entry:values.entrySet())out=out.replace("{{"+entry.getKey()+"}}",entry.getValue());return out;}
    private static String markdown(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private SubscriptionSchedule readSchedule(Object raw,int interval){if(raw==null||String.valueOf(raw).isBlank())return new SubscriptionSchedule("interval",Math.max(15,Math.min(10080,interval)),"",List.of(),"UTC");try{return json.readValue(String.valueOf(raw),SubscriptionSchedule.class);}catch(Exception e){throw new IllegalArgumentException("订阅日历配置无效");}}
    private List<String> readList(Object raw,List<String> fallback){if(raw==null)return fallback;try{@SuppressWarnings("unchecked") List<String> result=(List<String>)json.readValue(String.valueOf(raw),List.class);return result==null?fallback:result;}catch(Exception e){return fallback;}}
    private static String defaultValue(Object value,String fallback){return value==null||String.valueOf(value).isBlank()?fallback:String.valueOf(value);}
    private static String clean(String text,int max){String value=text==null?"":text.trim();return value.length()>max?value.substring(0,max):value;}
}
