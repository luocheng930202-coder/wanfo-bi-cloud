package com.wanfo.bi.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.mapper.BiDatasetFieldMapper;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.mapper.DpQualityRuleVersionMapper;
import com.wanfo.bi.service.DatasetQualityRuleService.Rule;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;

import tools.jackson.databind.ObjectMapper;

@Service
public class QualityRuleVersionService {
    private static final Set<String> TYPES=Set.of("NOT_NULL","UNIQUE","NULL_RATE_MAX","RANGE","REGEX","FRESHNESS","RECONCILIATION");
    private static final Set<String> SEVERITIES=Set.of("INFO","WARN","ERROR","FATAL");
    public record RuleInput(String code,String type,String field,String severity,Boolean enabled,Map<String,Object> config) {}
    private final DpQualityRuleVersionMapper mapper;
    private final BiDatasetTableMapper datasets;
    private final BiDatasetFieldMapper fields;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider capabilities;
    private final ObjectMapper json;

    public QualityRuleVersionService(DpQualityRuleVersionMapper mapper,BiDatasetTableMapper datasets,
            BiDatasetFieldMapper fields,BiResourceAuthorizer resources,CapabilityProvider capabilities,ObjectMapper json) {
        this.mapper=mapper;this.datasets=datasets;this.fields=fields;this.resources=resources;this.capabilities=capabilities;this.json=json;
    }

    public List<Map<String,Object>> list(Long datasetId) { requireAccess(datasetId,false);return mapper.listSets(datasetId); }

    public List<Map<String,Object>> rules(Long datasetId,Long versionId) {
        requireAccess(datasetId,false);Map<String,Object> set=requireSet(datasetId,versionId);
        List<Map<String,Object>> result=mapper.listRules(versionId);
        result.forEach(rule->{Object raw=rule.get("config");if(raw instanceof String text){try{rule.put("config",json.readValue(text,Object.class));}catch(RuntimeException error){throw new IllegalStateException("质量规则版本配置损坏");}}});
        return result;
    }

    public List<Map<String,Object>> publishedRules(Long datasetId,Long versionId) {
        requirePublishedSet(datasetId,versionId);
        List<Map<String,Object>> result=mapper.listRules(versionId);
        result.forEach(rule->{Object raw=rule.get("config");if(raw instanceof String text){try{rule.put("config",json.readValue(text,Object.class));}catch(RuntimeException error){throw new IllegalStateException("质量规则版本配置损坏");}}});
        return result;
    }

    public void requirePublishedSet(Long datasetId,Long versionId) {
        loadPublishedSet(datasetId,versionId);
    }

    @Transactional
    public Map<String,Object> createDraft(Long datasetId,String name,String remark,List<RuleInput> inputs,String actor) {
        requireAccess(datasetId,true);requireDataset(datasetId);
        if(name==null||name.isBlank()||name.trim().length()>160)throw new IllegalArgumentException("规则集名称须为 1～160 字");
        List<RuleInput> safe=validate(datasetId,inputs);
        Map<String,Object> row=new LinkedHashMap<>();row.put("datasetId",datasetId);row.put("version",mapper.nextVersion(datasetId));row.put("name",name.trim());row.put("actor",safeActor(actor));row.put("remark",remark==null?null:remark.substring(0,Math.min(500,remark.length())));
        if(mapper.insertSet(row)!=1||!(row.get("id") instanceof Number id))throw new IllegalStateException("创建质量规则草稿失败");
        persistRules(id.longValue(),safe);
        return mapper.selectSet(datasetId,id.longValue());
    }

    @Transactional
    public Map<String,Object> copyDraft(Long datasetId,Long sourceVersionId,String actor) {
        requireAccess(datasetId,true);Map<String,Object> source=requireSet(datasetId,sourceVersionId);
        List<RuleInput> inputs=new ArrayList<>();for(Map<String,Object> rule:mapper.listRules(sourceVersionId)){
            Object raw=rule.get("config");Map<String,Object> config=raw instanceof String text?readConfig(text):raw instanceof Map<?,?> map?castMap(map):Map.of();
            Object enabled=rule.get("enabled");boolean isEnabled=Boolean.TRUE.equals(enabled)||(enabled instanceof Number number&&number.intValue()==1);
            inputs.add(new RuleInput(String.valueOf(rule.get("code")),String.valueOf(rule.get("type")),(String)rule.get("field"),String.valueOf(rule.get("severity")),isEnabled,config));
        }
        return createDraft(datasetId,String.valueOf(source.get("name"))+" (草稿)",String.valueOf(source.getOrDefault("remark","")),inputs,actor);
    }

    @Transactional
    public Map<String,Object> publish(Long datasetId,Long versionId,String actor) {
        requireAccess(datasetId,true);Map<String,Object> set=requireSet(datasetId,versionId);
        if(!"DRAFT".equals(String.valueOf(set.get("status"))))throw new IllegalArgumentException("只有草稿版本可以发布");
        List<Map<String,Object>> rules=mapper.listRules(versionId);String hash=definitionHash(rules);
        if(mapper.publish(datasetId,versionId,hash,safeActor(actor))!=1)throw new IllegalStateException("发布状态已变化，请刷新规则版本");
        return mapper.selectSet(datasetId,versionId);
    }

    public Map<String,Object> previewLegacyMigration(List<Long> requestedDatasetIds) {
        capabilities.require("bi:resource:admin");Set<Long> selected=requestedDatasetIds==null||requestedDatasetIds.isEmpty()?null:Set.copyOf(requestedDatasetIds);
        List<Map<String,Object>> rows=mapper.legacyRows();List<Map<String,Object>> items=new ArrayList<>();int eligible=0,already=0,blocked=0;
        for(Map<String,Object> row:rows){Long datasetId=((Number)row.get("datasetId")).longValue();if(selected!=null&&!selected.contains(datasetId))continue;
            Map<String,Object> item=new LinkedHashMap<>();item.put("datasetId",datasetId);item.put("updateBy",row.get("updateBy"));
            if(mapper.legacyImported(datasetId)>0){already++;item.put("status","ALREADY_IMPORTED");item.put("ruleCount",0);}
            else try{List<RuleInput> rules=convertLegacy(datasetId,String.valueOf(row.get("rulesJson")));item.put("status","READY");item.put("ruleCount",rules.size());eligible++;}
            catch(RuntimeException failure){item.put("status","BLOCKED");item.put("summary",safeMessage(failure));blocked++;}
            items.add(item);
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("source","bi_dataset_quality_rule V25");result.put("total",items.size());result.put("eligible",eligible);result.put("alreadyImported",already);result.put("blocked",blocked);result.put("items",items);return result;
    }

    @Transactional
    public Map<String,Object> migrateLegacyRules(List<Long> requestedDatasetIds,String actor) {
        capabilities.require("bi:resource:admin");
        if(requestedDatasetIds==null||requestedDatasetIds.isEmpty()||requestedDatasetIds.size()>100)throw new IllegalArgumentException("请选择 1～100 个待迁移数据集");
        Set<Long> selected=Set.copyOf(requestedDatasetIds);Map<Long,Map<String,Object>> byId=new LinkedHashMap<>();
        for(Map<String,Object> row:mapper.legacyRows()){Long id=((Number)row.get("datasetId")).longValue();if(selected.contains(id))byId.put(id,row);}
        if(byId.size()!=selected.size())throw new IllegalArgumentException("部分数据集没有可迁移的 V25 规则记录，请重新预览");
        List<Long> migrated=new ArrayList<>(),skipped=new ArrayList<>();
        for(Long datasetId:selected){
            if(mapper.legacyImported(datasetId)>0){skipped.add(datasetId);continue;}
            Map<String,Object> source=byId.get(datasetId);List<RuleInput> rules=convertLegacy(datasetId,String.valueOf(source.get("rulesJson")));
            Map<String,Object> draft=createDraft(datasetId,"V25 兼容规则迁移","Imported from bi_dataset_quality_rule V25",rules,actor);
            Long versionId=((Number)draft.get("id")).longValue();publish(datasetId,versionId,actor);migrated.add(datasetId);
        }
        return Map.of("migratedDatasetIds",migrated,"alreadyImportedDatasetIds",skipped,"migratedCount",migrated.size());
    }

    private List<RuleInput> convertLegacy(Long datasetId,String raw) {
        if(raw==null||raw.isBlank())throw new IllegalArgumentException("旧规则 JSON 为空");
        Rule[] old;try{old=json.readValue(raw,Rule[].class);}catch(RuntimeException failure){throw new IllegalArgumentException("旧规则 JSON 无法解析");}
        if(old==null||old.length>200)throw new IllegalArgumentException("旧规则数量无效");List<RuleInput> converted=new ArrayList<>();int sequence=0;
        for(Rule rule:old){if(rule==null||rule.field()==null)throw new IllegalArgumentException("旧规则缺少字段");
            if(rule.required())converted.add(new RuleInput("legacy."+(++sequence)+".required","NOT_NULL",rule.field(),"ERROR",true,Map.of("emptyStringIsNull",true)));
            if(rule.unique())converted.add(new RuleInput("legacy."+(++sequence)+".unique","UNIQUE",rule.field(),"ERROR",true,Map.of()));
            if(rule.maxNullRate()!=null)converted.add(new RuleInput("legacy."+(++sequence)+".null_rate","NULL_RATE_MAX",rule.field(),"WARN",true,Map.of("maxRate",rule.maxNullRate())));
        }
        return validate(datasetId,converted);
    }
    private static String safeMessage(RuntimeException error){String message=error.getMessage();if(message==null||message.isBlank())return "规则定义无效";return message.substring(0,Math.min(240,message.length()));}

    private List<RuleInput> validate(Long datasetId,List<RuleInput> inputs) {
        if(inputs==null||inputs.size()>200)throw new IllegalArgumentException("质量规则最多 200 条");
        Set<String> fieldNames=new java.util.HashSet<>();fields.selectByDatasetId(datasetId).forEach(field->fieldNames.add(field.getOriginName()));
        Set<String> codes=new java.util.HashSet<>();List<RuleInput> result=new ArrayList<>();
        for(RuleInput input:inputs){
            if(input==null||input.code()==null||!input.code().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,95}")||!codes.add(input.code()))throw new IllegalArgumentException("规则编码无效或重复");
            if(input.type()==null||!TYPES.contains(input.type()))throw new IllegalArgumentException("不支持的规则类型");
            if(input.field()!=null&&!fieldNames.contains(input.field()))throw new IllegalArgumentException("规则字段不存在: "+input.field());
            if(Set.of("NOT_NULL","UNIQUE","NULL_RATE_MAX","RANGE","REGEX").contains(input.type())&&input.field()==null)throw new IllegalArgumentException("该规则类型必须指定字段");
            String severity=input.severity()==null?"ERROR":input.severity();if(!SEVERITIES.contains(severity))throw new IllegalArgumentException("规则严重度无效");
            Map<String,Object> config=input.config()==null?Map.of():new TreeMap<>(input.config());
            if(config.size()>16||config.containsValue(null)||json.writeValueAsString(config).length()>8192)throw new IllegalArgumentException("规则配置过大或包含空值");
            validateConfig(input.type(),config);
            result.add(new RuleInput(input.code(),input.type(),input.field(),severity,input.enabled()==null||input.enabled(),Map.copyOf(config)));
        }
        return List.copyOf(result);
    }

    private void persistRules(long setId,List<RuleInput> rules) {
        for(RuleInput rule:rules)if(mapper.insertRule(setId,rule.code(),rule.type(),rule.field(),rule.severity(),Boolean.TRUE.equals(rule.enabled()),json.writeValueAsString(new TreeMap<>(rule.config())))!=1)throw new IllegalStateException("保存质量规则失败");
    }
    private static void validateConfig(String type,Map<String,Object> config){
        switch(type){
            case "NOT_NULL" -> {if(!Set.of("emptyStringIsNull").containsAll(config.keySet())||config.containsKey("emptyStringIsNull")&&!(config.get("emptyStringIsNull") instanceof Boolean))throw new IllegalArgumentException("NOT_NULL 配置无效");}
            case "UNIQUE" -> {if(!config.isEmpty())throw new IllegalArgumentException("UNIQUE 暂不接受额外配置");}
            case "NULL_RATE_MAX" -> {if(!config.keySet().equals(Set.of("maxRate")))throw new IllegalArgumentException("NULL_RATE_MAX 需要 maxRate 配置");double rate=decimalConfig(config.get("maxRate"));if(rate<0||rate>1)throw new IllegalArgumentException("maxRate 须在 0～1 之间");}
            case "RANGE" -> {if(config.isEmpty()||!Set.of("min","max").containsAll(config.keySet()))throw new IllegalArgumentException("RANGE 至少需要 min 或 max 配置");Double min=config.containsKey("min")?decimalConfig(config.get("min")):null;Double max=config.containsKey("max")?decimalConfig(config.get("max")):null;if(min!=null&&max!=null&&min>max)throw new IllegalArgumentException("RANGE 的 min 不能大于 max");}
            case "REGEX" -> {if(!config.keySet().equals(Set.of("pattern"))||!(config.get("pattern") instanceof String pattern)||pattern.isBlank()||pattern.length()>512)throw new IllegalArgumentException("REGEX 需要 1～512 字符的 pattern");try{Pattern.compile(pattern);}catch(RuntimeException error){throw new IllegalArgumentException("REGEX pattern 格式无效");}}
            case "FRESHNESS" -> {if(!config.keySet().equals(Set.of("maxAgeSeconds")))throw new IllegalArgumentException("FRESHNESS 需要 maxAgeSeconds 配置");long age=integerConfig(config.get("maxAgeSeconds"));if(age<1||age>31_536_000)throw new IllegalArgumentException("maxAgeSeconds 须在 1～31536000 之间");}
            case "RECONCILIATION" -> {if(!config.keySet().equals(Set.of("tolerance")))throw new IllegalArgumentException("RECONCILIATION 需要 tolerance 配置");if(decimalConfig(config.get("tolerance"))<0)throw new IllegalArgumentException("对账容差不能为负数");}
            default -> throw new IllegalArgumentException("不支持的规则类型");
        }
    }
    private static double decimalConfig(Object value){try{if(!(value instanceof Number))throw new NumberFormatException();double n=new BigDecimal(String.valueOf(value)).doubleValue();if(!Double.isFinite(n))throw new NumberFormatException();return n;}catch(RuntimeException error){throw new IllegalArgumentException("规则配置需要有限数值");}}
    private static long integerConfig(Object value){try{if(!(value instanceof Number))throw new NumberFormatException();return new BigDecimal(String.valueOf(value)).longValueExact();}catch(RuntimeException error){throw new IllegalArgumentException("规则配置需要整数秒数");}}
    private void requireAccess(Long datasetId,boolean edit) {capabilities.require(edit?"bi:datasource:model":"bi:dataset:query");BiDatasetTableDO dataset=requireDataset(datasetId);if(edit){long group=dataset.getGroupId()==null?0:dataset.getGroupId();if(group!=0)resources.require("dataset-group",group,"edit");}}
    private BiDatasetTableDO requireDataset(Long datasetId) {if(datasetId==null||datasetId<1)throw new IllegalArgumentException("数据集编号无效");resources.require("dataset",datasetId,"view");BiDatasetTableDO dataset=datasets.selectById(datasetId);if(dataset==null||!"table".equals(dataset.getType()))throw new IllegalArgumentException("仅表数据集支持版本化质量规则");return dataset;}
    private Map<String,Object> requireSet(Long datasetId,Long versionId) {Map<String,Object> set=mapper.selectSet(datasetId,versionId);if(set==null)throw new IllegalArgumentException("质量规则版本不存在");return set;}
    private Map<String,Object> loadPublishedSet(Long datasetId,Long versionId) {requireAccess(datasetId,false);Map<String,Object> set=requireSet(datasetId,versionId);if(!"PUBLISHED".equals(String.valueOf(set.get("status"))))throw new IllegalArgumentException("流程质量门禁只能引用已发布规则版本");return set;}
    private String definitionHash(List<Map<String,Object>> rules) {try{List<Map<String,Object>> stable=rules.stream().map(rule->{Map<String,Object> item=new TreeMap<>(rule);item.remove("id");item.remove("ruleSetId");Object raw=item.get("config");if(raw instanceof String text)item.put("config",readConfig(text));return item;}).toList();byte[] bytes=MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(stable).getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(bytes);}catch(Exception error){throw new IllegalStateException("规则集版本哈希计算失败",error);}}
    private Map<String,Object> readConfig(String text) {try{return json.readValue(text,Map.class);}catch(RuntimeException error){throw new IllegalStateException("质量规则配置损坏");}}
    private static Map<String,Object> castMap(Map<?,?> map){Map<String,Object> out=new LinkedHashMap<>();map.forEach((k,v)->out.put(String.valueOf(k),v));return out;}
    private static String safeActor(String actor){return actor==null||actor.isBlank()?"system":actor.substring(0,Math.min(64,actor.length()));}
}
