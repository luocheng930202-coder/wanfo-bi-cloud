package com.wanfo.bi.security;

import java.util.*;
import com.wanfo.bi.mapper.BiDatasetPolicyMapper;
import com.wanfo.bi.mapper.BiDatasetFieldMapper;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.mapper.BiResourceMapper;
import org.springframework.stereotype.Service;

/** Host-independent fixed restrictions. All applicable row rules intersect; DENY wins over MASK. */
@Service
public class DatasetDataPolicy {
    public record Rule(Long id, Long datasetId, Long principalId, String fieldName, String ruleKind, String ruleValue, String createBy) { }
    public record Scope(List<Rule> rows, Set<String> denied, Set<String> masked) {
        public static Scope unrestricted() { return new Scope(List.of(),Set.of(),Set.of()); }
    }
    private static final Set<String> KINDS=Set.of("EQ","NE","GT","GE","LT","LE","IS_NULL","IS_NOT_NULL","DENY","MASK");
    private final BiDatasetPolicyMapper mapper;
    private final CurrentActorProvider actors;
    private final CapabilityProvider caps;
    private final BiDatasetFieldMapper fields;
    private final BiDatasetTableMapper datasets;
    private final BiResourceMapper resources;
    public DatasetDataPolicy(BiDatasetPolicyMapper mapper, CurrentActorProvider actors, CapabilityProvider caps,
            BiDatasetFieldMapper fields, BiDatasetTableMapper datasets, BiResourceMapper resources) {
        this.mapper=mapper;this.actors=actors;this.caps=caps;this.fields=fields;this.datasets=datasets;this.resources=resources;
    }
    public Scope resolve(Long datasetId) {
        if(caps.has("bi:resource:admin")) return Scope.unrestricted();
        BiActor actor=actors.currentActor();
        Set<Long> ids=new HashSet<>(actor.roleIds());ids.add(actor.userId());
        if(actor.organizationId()!=null) ids.add(actor.organizationId());
        List<Rule> rows=new ArrayList<>();Set<String> denied=new HashSet<>(), masked=new HashSet<>();
        for(Rule r:mapper.list(datasetId)) {
            if(!ids.contains(r.principalId())) continue;
            if((r.ruleKind()==null || !KINDS.contains(r.ruleKind()))) throw new BiAccessException(403,"数据权限规则无效，请联系管理员");
            switch(r.ruleKind()) {case "DENY" -> denied.add(r.fieldName());case "MASK" -> masked.add(r.fieldName());default -> rows.add(r);}
        }
        masked.removeAll(denied);
        return DerivedFieldPolicy.expand(fields.selectByDatasetId(datasetId),new Scope(List.copyOf(rows),Set.copyOf(denied),Set.copyOf(masked)));
    }
    private void requireTable(Long id) {
        caps.require("bi:resource:admin");
        var ds=datasets.selectById(id);
        if(ds==null || !"table".equals(ds.getType())) throw new IllegalArgumentException("仅支持表数据集的数据权限配置");
    }
    public List<Rule> list(Long id) { requireTable(id);return mapper.list(id); }
    public void add(Long id,Rule r) {
        requireTable(id);
        if(r.principalId()==null || resources.principalExists(r.principalId())!=1 || (r.ruleKind()==null || !KINDS.contains(r.ruleKind()))
                || fields.selectByDatasetId(id).stream().noneMatch(f->Objects.equals(f.getOriginName(),r.fieldName())))
            throw new IllegalArgumentException("请选择有效主体、字段和规则");
        boolean valueRequired=!Set.of("DENY","MASK","IS_NULL","IS_NOT_NULL").contains(r.ruleKind());
        if(valueRequired && (r.ruleValue()==null || r.ruleValue().length()>1000)) throw new IllegalArgumentException("规则值不能为空且不能超过 1000 字符");
        mapper.insert(new Rule(null,id,r.principalId(),r.fieldName(),r.ruleKind(),valueRequired?r.ruleValue():null,actors.currentActor().username()));
    }
    public void remove(Long id,Long ruleId) {requireTable(id);mapper.delete(id,ruleId);}
    /** Called only inside the dataset deletion transaction, after resource authorization. */
    public void deleteWithDataset(Long id) {mapper.deleteWithDataset(id);}
}
