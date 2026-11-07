package com.wanfo.bi.service;

import java.math.BigDecimal;
import java.util.*;
import com.wanfo.bi.domain.dataset.DatasetJoinModel;
import com.wanfo.bi.mapper.LineageMapper;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Rebuilds safe, explicit lineage edges from catalog hierarchy and BI resource references. */
@Service
public class LineageCollectionService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LineageCollectionService.class);
    private static final int MAX_ASSETS=10_000;
    private static final int MAX_DATASETS=5_000;
    private final LineageMapper mapper;
    private final LineageService lineage;
    private final CapabilityProvider capabilities;
    private final CurrentActorProvider actors;
    private final CatalogAssetService catalog;
    private final ObjectMapper json;
    private final ClassificationLineageService classificationLineage;

    public LineageCollectionService(LineageMapper mapper,LineageService lineage,CapabilityProvider capabilities,
            CurrentActorProvider actors,CatalogAssetService catalog,ObjectMapper json,ClassificationLineageService classificationLineage){
        this.mapper=mapper;this.lineage=lineage;this.capabilities=capabilities;this.actors=actors;this.catalog=catalog;this.json=json;
        this.classificationLineage=classificationLineage;
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> rebuild(){
        capabilities.require("bi:resource:admin");
        return collect();
    }

    /** Rebuild after a scan or publish. A missing session or a collector error does not undo that change. */
    @Transactional(rollbackFor=Exception.class)
    public void rebuildAfterChange(){
        try{actors.currentActor();collect();}
        catch(RuntimeException ex){
            LOGGER.warn("血缘自动重建未执行：{}",ex.getMessage());
            org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    private Map<String,Object> collect(){
        catalog.syncPublishedDataApis();
        List<Map<String,Object>> assets=mapper.lineageAssets();
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
            if(parent!=null&&byId.containsKey(parent)&&active(byId.get(parent))&&active(asset)){
                String parentType=String.valueOf(byId.get(parent).get("assetType"));
                if("COLUMN".equals(type)&&"TABLE".equals(parentType)&&"CONNECTOR".equals(String.valueOf(asset.get("sourceKind")))){
                    add(targetParent(parent,target,"TABLE","CONTAINS","CATALOG",Map.of("discovery","table-column")),observed);discovered++;
                }else if(!isColumnType(parentType)&&!isColumnType(type)){
                    String kind="CONTAINS";
                    if("BI_RESOURCE".equals(asset.get("sourceKind"))&&Set.of("CHART","METRIC").contains(type))kind="REFERENCES";
                    add(targetParent(parent,target,"TABLE",kind,"CATALOG",Map.of("discovery","asset-parent")),observed);discovered++;
                }
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
                add(targetParent(parent,target,"TABLE","CONTAINS","CATALOG",Map.of("discovery","dataset-field-parent")),observed);discovered++;
            }
            if("METRIC".equals(sourceKind)&&datasetId!=null){
                Long datasetAsset=biIds.get(key("DATASET",datasetId));
                if(datasetAsset!=null&&active(byId.get(datasetAsset))&&active(asset)){
                    add(targetParent(datasetAsset,target,"TABLE","DERIVES","RESOURCE_BINDING",Map.of("discovery","metric-dataset-derives")),observed);discovered++;
                    for(Map<String,Object> field:assets){
                        if(!"DATASET_FIELD".equals(field.get("assetType")))continue;
                        if(!Objects.equals(number(field.get("parentId")),datasetAsset)&&!Objects.equals(number(field.get("parentSourceId")),datasetId))continue;
                        if(!active(field))continue;
                        add(targetParent(number(field.get("id")),target,"COLUMN","REFERENCES","RESOURCE_BINDING",Map.of("discovery","metric-field-reference","field",String.valueOf(field.get("name")),"fieldScope","DATASET_ALL_FIELDS")),observed);discovered++;
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
        discovered+=collectDeclaredSources(assets,byId,biIds,connectorTables,observed);
        int stale=0;for(Map<String,Object> old:mapper.generatedEdges()){
            if(!observed.contains(String.valueOf(old.get("edgeKey")))){lineage.markGenerated(number(old.get("id")),"STALE");stale++;}
        }
        Map<String,Object> inheritance=classificationLineage.recomputeGenerated();
        Map<String,Object> summary=new LinkedHashMap<>();
        summary.put("assetsScanned",assets.size());
        summary.put("candidatesDiscovered",discovered);
        summary.put("activeEdgesObserved",observed.size());
        summary.put("staleEdgesMarked",stale);
        summary.put("sourceMethods",List.of("CATALOG","RESOURCE_BINDING","AUTO_SQL","PIPELINE"));
        summary.put("classificationInheritance",inheritance);
        summary.put("at",java.time.LocalDateTime.now().toString());
        return summary;
    }

    private int collectDeclaredSources(List<Map<String,Object>> assets,Map<Long,Map<String,Object>> byId,Map<String,Long> biIds,
            Map<String,List<Long>> connectorTables,Set<String> observed){
        List<Map<String,Object>> definitions=mapper.datasetDefinitions();
        if(definitions.size()>MAX_DATASETS)throw new IllegalStateException("数据集超过血缘采集上限，请减少后再重建");
        Map<Long,LineageSourcePlanner.Read> reads=new HashMap<>();
        for(Map<String,Object> row:definitions){Long id=number(row.get("id"));if(id!=null)reads.put(id,LineageSourcePlanner.read(Objects.toString(row.get("sqlText"),null),Objects.toString(row.get("modelJson"),null)));}
        Map<Long,Map<String,List<Long>>> columns=new HashMap<>();
        for(Map<String,Object> asset:assets){
            if(!"CONNECTOR".equals(asset.get("sourceKind"))||!"COLUMN".equals(asset.get("assetType"))||!active(asset))continue;
            Long parent=number(asset.get("parentId"));Long id=number(asset.get("id"));if(parent==null||id==null)continue;
            columns.computeIfAbsent(parent,ignored->new HashMap<>()).computeIfAbsent(String.valueOf(asset.get("name")).toLowerCase(Locale.ROOT),ignored->new ArrayList<>()).add(id);
        }
        int discovered=0;
        for(Map<String,Object> asset:assets){
            if(!"BI_RESOURCE".equals(asset.get("sourceKind"))||!active(asset))continue;
            String type=String.valueOf(asset.get("assetType"));Long target=number(asset.get("id"));
            Map<String,Object> metadata=parse(asset.get("metadataJson"));
            if("DATA_API".equals(type)){
                Long metric=biIds.get(key("METRIC",number(metadata.get("metricId"))));
                if(metric!=null&&active(byId.get(metric))){add(targetParent(metric,target,"TABLE","REFERENCES","RESOURCE_BINDING",Map.of("discovery","metric-data-api")),observed);discovered++;}
                continue;
            }
            if(!"DATASET".equals(type))continue;
            Long datasetId=number(asset.get("sourceId"));LineageSourcePlanner.Read read=reads.get(datasetId);if(read==null)continue;
            Long datasource=number(metadata.get("datasourceId"));
            String sourceTable=Objects.toString(metadata.get("sourceTable"),"").trim();
            List<Long> joinTargets=new ArrayList<>();boolean unresolved=read.incomplete();
            for(String name:read.joinTables()){Long table=matchTable(connectorTables,byId,datasource,name);joinTargets.add(table);if(table==null)unresolved=true;}
            List<String> sqlNames=new ArrayList<>();List<Long> sqlTargets=new ArrayList<>();
            for(String name:read.tables()){
                if(read.joinTables().stream().anyMatch(name::equalsIgnoreCase))continue;
                if(!sourceTable.isEmpty()&&name.equalsIgnoreCase(LineageSourcePlanner.normalize(sourceTable)))continue;
                Long table=matchTable(connectorTables,byId,datasource,name);if(table==null)unresolved=true;else{sqlNames.add(name);sqlTargets.add(table);}
            }
            if(unresolved){
                Long sourceAsset=biIds.get(key("DATASOURCE",datasource));
                if(sourceAsset!=null&&active(byId.get(sourceAsset))){add(targetParent(sourceAsset,target,"TABLE","REFERENCES","RESOURCE_BINDING",Map.of("discovery","dataset-datasource-reference","completeness","INCOMPLETE")),observed);discovered++;}
                Long baseTable=matchTable(connectorTables,byId,datasource,sourceTable);
                if(baseTable!=null){add(targetParent(baseTable,target,"TABLE","REFERENCES","RESOURCE_BINDING",Map.of("discovery","dataset-source-table-reference","completeness","INCOMPLETE")),observed);discovered++;}
            }
            for(int i=0;i<joinTargets.size();i++){
                Long table=joinTargets.get(i);if(table==null)continue;
                Map<String,Object> evidence=new LinkedHashMap<>();evidence.put("discovery","dataset-join-table");evidence.put("table",read.joinTables().get(i));
                if(unresolved)evidence.put("completeness","INCOMPLETE");
                add(targetParent(table,target,"TABLE","REFERENCES","RESOURCE_BINDING",evidence),observed);discovered++;
                if(i<8)discovered+=linkFields(columns.getOrDefault(table,Map.of()),fieldsOf(assets,target,datasetId),DatasetJoinModel.fieldPrefix(i+1),true,observed);
            }
            for(int i=0;i<sqlTargets.size();i++){
                Map<String,Object> evidence=new LinkedHashMap<>();evidence.put("discovery","dataset-sql-table");evidence.put("table",sqlNames.get(i));
                if(unresolved)evidence.put("completeness","INCOMPLETE");
                add(targetParent(sqlTargets.get(i),target,"TABLE","REFERENCES","AUTO_SQL",evidence),observed);discovered++;
            }
            Long base=matchTable(connectorTables,byId,datasource,sourceTable);
            if(base==null&&read.joinTables().isEmpty()&&sqlTargets.size()==1&&!unresolved)base=sqlTargets.get(0);
            if(base!=null)discovered+=linkFields(columns.getOrDefault(base,Map.of()),fieldsOf(assets,target,datasetId),"",false,observed);
        }
        for(Map<String,Object> write:mapper.pipelineWrites())discovered+=writeEdge(biIds,byId,connectorTables,observed,number(write.get("datasetId")),null,number(write.get("targetDatasourceId")),Objects.toString(write.get("targetTable"),""),"pipeline-warehouse-publish");
        for(Map<String,Object> write:mapper.objectLoadWrites())discovered+=writeEdge(biIds,byId,connectorTables,observed,null,number(write.get("sourceDatasourceId")),number(write.get("targetDatasourceId")),Objects.toString(write.get("targetTable"),""),"object-storage-load");
        for(Map<String,Object> write:mapper.cdcWrites()){
            Long staging=matchTable(connectorTables,byId,number(write.get("sourceDatasourceId")),Objects.toString(write.get("stagingTable"),""));
            Long targetTable=matchTable(connectorTables,byId,number(write.get("targetDatasourceId")),Objects.toString(write.get("targetTable"),""));
            if(staging!=null&&targetTable!=null){add(targetParent(staging,targetTable,"TABLE","DERIVES","PIPELINE",Map.of("discovery","cdc-ingest")),observed);discovered++;}
            else discovered+=writeEdge(biIds,byId,connectorTables,observed,null,number(write.get("sourceDatasourceId")),number(write.get("targetDatasourceId")),Objects.toString(write.get("targetTable"),""),"cdc-ingest");
        }
        return discovered;
    }

    private int writeEdge(Map<String,Long> biIds,Map<Long,Map<String,Object>> byId,Map<String,List<Long>> connectorTables,Set<String> observed,
            Long datasetId,Long sourceDatasourceId,Long targetDatasourceId,String targetTable,String discovery){
        Long target=matchTable(connectorTables,byId,targetDatasourceId,targetTable);if(target==null)return 0;
        Long source=datasetId!=null?biIds.get(key("DATASET",datasetId)):biIds.get(key("DATASOURCE",sourceDatasourceId));
        if(source==null||!active(byId.get(source))||Objects.equals(source,target))return 0;
        add(targetParent(source,target,"TABLE","DERIVES","PIPELINE",Map.of("discovery",discovery)),observed);return 1;
    }

    private int linkFields(Map<String,List<Long>> columns,List<Map<String,Object>> fields,String prefix,boolean prefixed,Set<String> observed){
        int discovered=0;
        for(Map<String,Object> field:fields){
            String name=String.valueOf(field.get("name"));
            String columnName=prefixed? (name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))?name.substring(prefix.length()):null) : name;
            if(columnName==null||columnName.isBlank())continue;
            List<Long> matches=columns.getOrDefault(columnName.toLowerCase(Locale.ROOT),List.of());
            Long column=LineageSourcePlanner.uniqueMatch(matches);if(column==null)continue;
            add(targetParent(column,number(field.get("id")),"COLUMN","REFERENCES","CATALOG",Map.of("discovery","physical-field-name","field",name)),observed);discovered++;
        }
        return discovered;
    }

    private static List<Map<String,Object>> fieldsOf(List<Map<String,Object>> assets,Long datasetAsset,Long datasetId){
        List<Map<String,Object>> fields=new ArrayList<>();
        for(Map<String,Object> field:assets){
            if(!"DATASET_FIELD".equals(field.get("assetType"))||!"ACTIVE".equals(field.get("status")))continue;
            if(Objects.equals(number(field.get("parentId")),datasetAsset)||Objects.equals(number(field.get("parentSourceId")),datasetId))fields.add(field);
        }
        return fields;
    }

    private static Long matchTable(Map<String,List<Long>> connectorTables,Map<Long,Map<String,Object>> byId,Long datasourceId,String name){
        if(datasourceId==null||name==null||name.isBlank())return null;
        return LineageSourcePlanner.uniqueMatch(connectorTables.getOrDefault(tableKey(datasourceId,name),List.of()).stream().filter(id->active(byId.get(id))).toList());
    }

    private void add(Map<String,Object> input,Set<String> observed){
        if(input.get("sourceAssetId")==null||input.get("targetAssetId")==null||input.get("sourceVersionId")==null||input.get("targetVersionId")==null)return;
        Map<String,Object> saved=lineage.persistGenerated(input);observed.add(String.valueOf(saved.get("edgeKey")));
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
