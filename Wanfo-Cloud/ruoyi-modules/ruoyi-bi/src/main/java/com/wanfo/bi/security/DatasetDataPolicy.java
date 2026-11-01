package com.wanfo.bi.security;

import java.util.*;
import com.wanfo.bi.mapper.BiDatasetPolicyMapper;
import com.wanfo.bi.mapper.BiDatasetFieldMapper;
import com.wanfo.bi.mapper.BiDatasetTableMapper;
import com.wanfo.bi.mapper.BiResourceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Host-independent fixed restrictions. All applicable row rules intersect; DENY wins over MASK. */
@Service
public class DatasetDataPolicy {
    public record Rule(Long id, Long datasetId, Long principalId, String fieldName, String ruleKind, String ruleValue, String createBy) { }
    public record Scope(List<Rule> rows, Set<String> denied, Set<String> masked, Map<String,List<String>> allowedValues) {
        public Scope(List<Rule> rows, Set<String> denied, Set<String> masked) { this(rows,denied,masked,Map.of()); }
        public Scope { allowedValues = Map.copyOf(allowedValues); }
        public static Scope unrestricted() { return new Scope(List.of(),Set.of(),Set.of(),Map.of()); }
    }
    private static final Set<String> KINDS=Set.of("EQ","NE","GT","GE","LT","LE","IS_NULL","IS_NOT_NULL","DENY","MASK");
    private static final ThreadLocal<Boolean> FORCE_ENFORCE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final BiDatasetPolicyMapper mapper;
    private final CurrentActorProvider actors;
    private final CapabilityProvider caps;
    private final BiDatasetFieldMapper fields;
    private final BiDatasetTableMapper datasets;
    private final BiResourceMapper resources;
    private final com.wanfo.bi.service.WarehouseScopeService warehouseScopes;

    /** Run with admin unrestricted bypass disabled (Data API / forced consumption). */
    public static void runEnforced(Runnable action) {
        Boolean previous = FORCE_ENFORCE.get();
        FORCE_ENFORCE.set(Boolean.TRUE);
        try { action.run(); }
        finally { FORCE_ENFORCE.set(previous); }
    }
    public DatasetDataPolicy(BiDatasetPolicyMapper mapper, CurrentActorProvider actors, CapabilityProvider caps,
            BiDatasetFieldMapper fields, BiDatasetTableMapper datasets, BiResourceMapper resources) {
        this(mapper,actors,caps,fields,datasets,resources,null);
    }
    @Autowired
    public DatasetDataPolicy(BiDatasetPolicyMapper mapper, CurrentActorProvider actors, CapabilityProvider caps,
            BiDatasetFieldMapper fields, BiDatasetTableMapper datasets, BiResourceMapper resources,
            com.wanfo.bi.service.WarehouseScopeService warehouseScopes) {
        this.mapper=mapper;this.actors=actors;this.caps=caps;this.fields=fields;this.datasets=datasets;this.resources=resources;this.warehouseScopes=warehouseScopes;
    }
    public Scope resolve(Long datasetId) {
        if(!Boolean.TRUE.equals(FORCE_ENFORCE.get()) && caps.has("bi:resource:admin")) return Scope.unrestricted();
        return resolveEnforced(datasetId);
    }

    /**
     * Applies principal dataset rules without the resource-admin unrestricted shortcut.
     * Used by Data API so owner-admin tokens cannot leak plaintext to key callers.
     */
    public Scope resolveEnforced(Long datasetId) {
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
        Scope scope = new Scope(List.copyOf(rows),Set.copyOf(denied),Set.copyOf(masked));
        if (warehouseScopes != null) scope = warehouseScopes.apply(datasetId, actor, scope);
        return DerivedFieldPolicy.expand(fields.selectByDatasetId(datasetId),scope);
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
