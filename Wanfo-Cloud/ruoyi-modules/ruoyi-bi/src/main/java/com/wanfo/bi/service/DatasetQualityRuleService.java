package com.wanfo.bi.service;

import com.wanfo.bi.domain.BiDatasetTableDO;
import com.wanfo.bi.mapper.BiDatasetFieldMapper;
import com.wanfo.bi.mapper.BiDatasetQualityRuleMapper;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.security.BiResourceAuthorizer;
import com.wanfo.bi.security.CapabilityProvider;
import java.util.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class DatasetQualityRuleService {
    public record Rule(String field,boolean required,boolean unique,Double maxNullRate){}
    private final BiDatasetQualityRuleMapper rules;
    private final BiDatasetFieldMapper fields;
    private final BiDatasetTableMapper datasets;
    private final BiResourceAuthorizer resources;
    private final CapabilityProvider caps;
    private final ObjectMapper json;
    public DatasetQualityRuleService(BiDatasetQualityRuleMapper rules,BiDatasetFieldMapper fields,BiDatasetTableMapper datasets,
            BiResourceAuthorizer resources,CapabilityProvider caps,ObjectMapper json){this.rules=rules;this.fields=fields;this.datasets=datasets;this.resources=resources;this.caps=caps;this.json=json;}

    public List<Rule> get(Long id){caps.require("bi:dataset:query");requireVisible(id);return decode(rules.get(id));}
    public List<Rule> save(Long id,List<Rule> input,String operator){caps.require("bi:datasource:model");BiDatasetTableDO dataset=requireVisible(id);long group=dataset.getGroupId()==null?0:dataset.getGroupId();if(group!=0)resources.require("dataset-group",group,"edit");
        if(input==null||input.size()>200)throw new IllegalArgumentException("质量规则最多 200 条");Set<String> valid=new HashSet<>();fields.selectByDatasetId(id).forEach(f->valid.add(f.getOriginName()));Set<String> seen=new HashSet<>();List<Rule> safe=new ArrayList<>();
        for(Rule rule:input){if(rule==null||rule.field()==null||!valid.contains(rule.field())||!seen.add(rule.field()))throw new IllegalArgumentException("质量规则字段不存在或重复");Double max=rule.maxNullRate();if(max!=null&&(!Double.isFinite(max)||max<0||max>1))throw new IllegalArgumentException("空值率阈值需在 0 到 1 之间");safe.add(new Rule(rule.field(),rule.required(),rule.unique(),max));}
        rules.save(id,json.writeValueAsString(safe),operator==null?"system":operator);return List.copyOf(safe);
    }
    public Map<String,Object> evaluate(Long id,Map<String,Object> report){List<Rule> configured=get(id);Map<String,Rule> byField=new HashMap<>();configured.forEach(r->byField.put(r.field(),r));List<Map<String,Object>> columns=new ArrayList<>();int violations=0;
        Object raw=report.get("columns");if(raw instanceof List<?> list)for(Object item:list)if(item instanceof Map<?,?> map){Map<String,Object> column=new LinkedHashMap<>();map.forEach((k,v)->column.put(String.valueOf(k),v));Rule rule=byField.get(String.valueOf(column.get("field")));List<String> issues=new ArrayList<>();if(rule!=null){int nulls=((Number)column.getOrDefault("nullCount",0)).intValue();int duplicates=((Number)column.getOrDefault("duplicateValueCount",0)).intValue();double rate=((Number)column.getOrDefault("nullRate",0)).doubleValue();if(rule.required()&&nulls>0)issues.add("存在空值");if(rule.unique()&&duplicates>0)issues.add("存在重复值");if(rule.maxNullRate()!=null&&rate>rule.maxNullRate())issues.add("空值率超过阈值");column.put("rule",rule);column.put("violations",issues);column.put("qualityStatus",issues.isEmpty()?"passed":"failed");violations+=issues.size();}else{column.put("violations",List.of());column.put("qualityStatus","unconfigured");}columns.add(column);}
        Map<String,Object> result=new LinkedHashMap<>(report);result.put("columns",columns);result.put("rules",configured);result.put("ruleFailureCount",violations);result.put("qualityStatus",violations==0?"passed":"failed");result.put("ruleNote","规则结果仍基于最多 100 行当前用户可见样本。");return result;
    }
    private BiDatasetTableDO requireVisible(Long id){resources.require("dataset",id,"view");BiDatasetTableDO row=datasets.selectById(id);if(row==null)throw new IllegalArgumentException("数据集不存在");return row;}
    private List<Rule> decode(String raw){if(raw==null||raw.isBlank())return List.of();try{return List.of(json.readValue(raw,Rule[].class));}catch(RuntimeException ex){throw new IllegalStateException("数据集质量规则损坏");}}
}
