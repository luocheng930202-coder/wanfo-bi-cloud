package com.wanfo.bi.service;

import java.math.BigDecimal;
import java.util.*;
import com.wanfo.bi.mapper.LineageMapper;
import com.wanfo.bi.security.CapabilityProvider;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Rebuilds safe, explicit lineage edges from catalog hierarchy and BI resource references. */
@Service
public class LineageCollectionService
{
    private static final int MAX_ASSETS=10_000;
    private final LineageMapper mapper;
    private final LineageService lineage;
    private final CapabilityProvider capabilities;
    private final ObjectMapper json;
    private final ClassificationLineageService classificationLineage;

    public LineageCollectionService(LineageMapper mapper,LineageService lineage,CapabilityProvider capabilities,
            ObjectMapper json,ClassificationLineageService classificationLineage){
        this.mapper=mapper;this.lineage=lineage;this.capabilities=capabilities;this.json=json;
        this.classificationLineage=classificationLineage;
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> rebuild(){
        capabilities.require("bi:resource:admin");List<Map<String,Object>> assets=mapper.lineageAssets();
        if(assets.size()>MAX_ASSETS)throw new IllegalStateException("目录资产超过血缘重建上限，请按数据源分批重建");
        Map<Long,Map<String,Object>> byId=new HashMap<>();Map<String,Long> biIds=new HashMap<>();
        Map<String,List<Long>> connectorTables=new HashMap<>();
        for(var asset:assets){Long id=number(asset.get("id"));if(id==null)continue;byId.put(id,asset);
            if("BI_RESOURCE".equals(asset.get("sourceKind")))biIds.put(key(String.valueOf(asset.get("assetType")),number(asset.get("sourceId"))),id);
            if("CONNECTOR".equals(asset.get("sourceKind"))&&"TABLE".equals(asset.get("assetType")))
                connectorTables.computeIfAbsent(tableKey(number(asset.get("sourceId")),String.valueOf(asset.get("name"))),ignored->new ArrayList<>()).add(id);
        }
        Set<String> observed=new HashSet<>();int discovered=0;
        for(Map<String,Object> asset:assets){
            Long target=number(asset.get("id")),parent=number(asset.get("parentId"));String type=String.valueOf(asset.get("assetType"));
            if(parent!=null&&byId.containsKey(parent)&&active(byId.get(parent))&&active(asset)
                    &&!isColumnType(String.valueOf(byId.get(parent).get("assetType")))
                    &&!isColumnType(type)){
                String kind="CONTAINS";
                if("BI_RESOURCE".equals(asset.get("sourceKind"))&&Set.of("CHART","METRIC").contains(type))kind="REFERENCES";
                add(targetParent(parent,target,"TABLE",kind,"CATALOG",Map.of("discovery","asset-parent")),observed);discovered++;
            }
            if("CONNECTOR".equals(asset.get("sourceKind"))&&"SCHEMA".equals(type)){
                Long datasourceAsset=biIds.get(key("DATASOURCE",number(asset.get("sourceId"))));
                if(datasourceAsset!=null&&active(byId.get(datasourceAsset))&&active(asset)){add(targetParent(datasourceAsset,target,"TABLE","CONTAINS","CATALOG",Map.of("discovery","connector-schema")),observed);discovered++;}
            }
            if(!"BI_RESOURCE".equals(asset.get("sourceKind")))continue;
            Map<String,Object> metadata=parse(asset.get("metadataJson"));
            String sourceKind=String.valueOf(asset.get("assetType"));
            Long datasource=number(metadata.get("datasourceId"));
            if(datasource!=null&&"DATASET".equals(sourceKind)){
                Long sourceAsset=biIds.get(key("DATASOURCE",datasource));
                if(sourceAsset!=null&&active(byId.get(sourceAsset))&&active(asset)){add(targetParent(sourceAsset,target,"TABLE","REFERENCES","RESOURCE_BINDING",Map.of("discovery","dataset-datasource-reference")),observed);discovered++;}
                String tableName=Objects.toString(metadata.get("sourceTable"),"").trim();
                if(!tableName.isEmpty()){
                    List<Long> tables=connectorTables.getOrDefault(tableKey(datasource,tableName),List.of());
                    if(tables.size()==1&&active(byId.get(tables.get(0)))&&active(asset)){add(targetParent(tables.get(0),target,"TABLE","REFERENCES","RESOURCE_BINDING",Map.of("discovery","dataset-source-table-reference")),observed);discovered++;}
                }
            }
            Long datasetId=number(metadata.get("datasetId"));
            if(datasetId!=null&&Set.of("CHART","METRIC").contains(sourceKind)){
                Long datasetAsset=biIds.get(key("DATASET",datasetId));
                if(datasetAsset!=null&&active(byId.get(datasetAsset))&&active(asset)){add(targetParent(datasetAsset,target,"TABLE","REFERENCES","RESOURCE_BINDING",Map.of("discovery","bi-resource-dataset-reference")),observed);discovered++;}
            }
            if("API_SOURCE".equals(sourceKind)&&datasource!=null){
                Long sourceAsset=biIds.get(key("DATASOURCE",datasource));
                if(sourceAsset!=null&&!Objects.equals(sourceAsset,target)&&active(byId.get(sourceAsset))&&active(asset)){add(targetParent(sourceAsset,target,"TABLE","REFERENCES","RESOURCE_BINDING",Map.of("discovery","api-source-reference")),observed);discovered++;}
            }
            if("DATASET_FIELD".equals(type)&&parent!=null&&byId.containsKey(parent)&&active(byId.get(parent))&&active(asset)){
                add(targetParent(parent,target,"COLUMN","CONTAINS","CATALOG",Map.of("discovery","dataset-field-parent")),observed);discovered++;
            }
            if("METRIC".equals(sourceKind)&&datasetId!=null){
                Long datasetAsset=biIds.get(key("DATASET",datasetId));
                if(datasetAsset!=null&&active(byId.get(datasetAsset))&&active(asset)){
                    add(targetParent(datasetAsset,target,"TABLE","DERIVES","RESOURCE_BINDING",Map.of("discovery","metric-dataset-derives")),observed);discovered++;
                    for(Map<String,Object> field:assets){
                        if(!"DATASET_FIELD".equals(field.get("assetType")))continue;
                        if(!Objects.equals(number(field.get("parentId")),datasetAsset)&&!Objects.equals(number(field.get("parentSourceId")),datasetId))continue;
                        if(!active(field))continue;
                        add(targetParent(number(field.get("id")),target,"COLUMN","REFERENCES","RESOURCE_BINDING",Map.of("discovery","metric-field-reference","field",String.valueOf(field.get("name")))),observed);discovered++;
                    }
                }
            }
        }
        for(Map<String,Object> binding:mapper.dashboardBindings()){
            Long dashboard=biIds.get(key("DASHBOARD",number(binding.get("dashboardId"))));
            Long chart=biIds.get(key("CHART",number(binding.get("chartId"))));Long dataset=biIds.get(key("DATASET",number(binding.get("datasetId"))));
            String kind=Objects.toString(binding.get("bindingKind"),"binding");
            if(dashboard!=null&&chart!=null&&active(byId.get(chart))&&active(byId.get(dashboard))){add(targetParent(chart,dashboard,"TABLE","REFERENCES","RESOURCE_BINDING",Map.of("discovery","dashboard-binding","bindingKind",kind)),observed);discovered++;}
            if(dashboard!=null&&dataset!=null&&active(byId.get(dataset))&&active(byId.get(dashboard))){add(targetParent(dataset,dashboard,"TABLE","REFERENCES","RESOURCE_BINDING",Map.of("discovery","dashboard-dataset-binding","bindingKind",kind)),observed);discovered++;}
        }
        for(Map<String,Object> load:mapper.warehousePublishBindings()){
            Long dataset=biIds.get(key("DATASET",number(load.get("datasetId"))));
            Long metric=biIds.get(key("METRIC",number(load.get("metricId"))));
            String tableName=Objects.toString(load.get("targetTable"),"").trim();
            Long targetDs=number(load.get("targetDatasourceId"));
            List<Long> tables=targetDs==null||tableName.isEmpty()?List.of():connectorTables.getOrDefault(tableKey(targetDs,tableName),List.of());
            if(tables.size()!=1)continue;
            Long warehouseTable=tables.get(0);
            if(!active(byId.get(warehouseTable)))continue;
            if(dataset!=null&&active(byId.get(dataset))){add(targetParent(dataset,warehouseTable,"TABLE","DERIVES","RESOURCE_BINDING",Map.of("discovery","dataset-warehouse-materialize","layer",String.valueOf(load.get("targetDatabase")))),observed);discovered++;}
            if(metric!=null&&active(byId.get(metric))){add(targetParent(metric,warehouseTable,"TABLE","DERIVES","RESOURCE_BINDING",Map.of("discovery","metric-warehouse-via-dataset")),observed);discovered++;}
        }
        int stale=0;for(Map<String,Object> old:mapper.generatedEdges()){
            if(!observed.contains(String.valueOf(old.get("edgeKey")))){lineage.invalidate(number(old.get("id")),"STALE");stale++;}
        }
        Map<String,Object> inheritance=classificationLineage.recomputeInheritance();
        Map<String,Object> summary=new LinkedHashMap<>();
        summary.put("assetsScanned",assets.size());
        summary.put("candidatesDiscovered",discovered);
        summary.put("activeEdgesObserved",observed.size());
        summary.put("staleEdgesMarked",stale);
        summary.put("sourceMethods",List.of("CATALOG","RESOURCE_BINDING"));
        summary.put("classificationInheritance",inheritance);
        summary.put("at",java.time.LocalDateTime.now().toString());
        return summary;
    }

    private void add(Map<String,Object> input,Set<String> observed){
        if(input.get("sourceAssetId")==null||input.get("targetAssetId")==null||input.get("sourceVersionId")==null||input.get("targetVersionId")==null)return;
        Map<String,Object> saved=lineage.save(input);observed.add(String.valueOf(saved.get("edgeKey")));
    }
    private Map<String,Object> targetParent(Long source,Long target,String level,String kind,String method,Map<String,Object> evidence){
        Map<String,Object> input=new LinkedHashMap<>();input.put("sourceAssetId",source);input.put("targetAssetId",target);input.put("lineageLevel",level);input.put("edgeKind",kind);
        input.put("sourceMethod",method);input.put("confidence",new BigDecimal("0.9800"));input.put("evidence",evidence);
        input.put("sourceVersionId",mapper.currentVersion(source));input.put("targetVersionId",mapper.currentVersion(target));return input;
    }
    private Map<String,Object> parse(Object value){try{if(value==null)return Map.of();Object parsed=json.readValue(String.valueOf(value),Object.class);return parsed instanceof Map<?,?> m?stringMap(m):Map.of();}catch(Exception ignored){return Map.of();}}
    private Map<String,Object> stringMap(Map<?,?> source){Map<String,Object> result=new LinkedHashMap<>();source.forEach((k,v)->result.put(String.valueOf(k),v));return result;}
    private static boolean active(Map<String,Object> asset){return asset!=null&&"ACTIVE".equals(asset.get("status"));}
    private static boolean isColumnType(String type){return Set.of("COLUMN","DATASET_FIELD").contains(type);}
    private static String key(String type,Object id){return type+":"+String.valueOf(id);}
    private static String tableKey(Long datasourceId,String name){String normalized=Objects.toString(name,"").trim().replace("`","").replace("\"","");int dot=normalized.lastIndexOf('.');if(dot>=0)normalized=normalized.substring(dot+1);return key("TABLE",datasourceId+":"+normalized.toLowerCase(Locale.ROOT));}
    private static Long number(Object value){if(value instanceof Number n)return n.longValue();try{return value==null?null:Long.valueOf(value.toString());}catch(NumberFormatException ignored){return null;}}
}
