package com.wanfo.bi.service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import com.wanfo.bi.mapper.BiExportJobMapper;
import com.wanfo.bi.security.*;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Async export of saved charts or published dashboard panels. Never accepts a client QueryModel. */
@Service
public class ExportJobService {
    public record Submit(String sourceType, Long sourceId, String panelKey, Integer revision, String format, String name, Map<String,Object> input) { }
    public record File(String name, String type, byte[] bytes, int rowCount) { }
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private final BiExportJobMapper mapper;
    private final ChartDefinitionService charts;
    private final DashboardService dashboards;
    private final CapabilityProvider caps;
    private final CurrentActorProvider actors;
    private final ObjectMapper json;
    private final Executor executor;
    private final LiveActorResolver live;
    public ExportJobService(BiExportJobMapper mapper, ChartDefinitionService charts, DashboardService dashboards,
            CapabilityProvider caps, CurrentActorProvider actors, ObjectMapper json, @Qualifier("biExportExecutor") Executor executor, LiveActorResolver live) {
        this.mapper=mapper;this.charts=charts;this.dashboards=dashboards;this.caps=caps;this.actors=actors;this.json=json;this.executor=executor;this.live=live;
    }
    @PostConstruct void recover() { mapper.recover(); mapper.purge(); }
    public List<Map<String,Object>> list() {
        caps.require("bi:export:list");
        mapper.purge();
        return mapper.list(actors.currentActor().userId()).stream().map(ExportJobService::publicJob).toList();
    }
    public Map<String,Object> get(Long id) {
        caps.require("bi:export:list");
        return publicJob(owned(id, false));
    }
    public Map<String,Object> submit(Submit request) {
        caps.require("bi:export:add");
        return asLive(actors.currentActor(),"bi:export:add",()->submitCurrent(request));
    }
    /** Generate the same permission-checked artifact as export jobs for a scheduled subscriber. */
    public File generateForSubscription(BiActor actor, Submit request) {
        return asLive(actor,"bi:export:add",()->{
            if (request == null || request.sourceId() == null || request.sourceId() < 1) throw new IllegalArgumentException("订阅资源无效");
            String type=request.sourceType()==null?"":request.sourceType().trim();
            String format=request.format()==null?"":request.format().trim().toLowerCase(Locale.ROOT);
            if(!Set.of("chart","dashboard").contains(type)||!Set.of("csv","xlsx","pdf").contains(format))throw new IllegalArgumentException("订阅来源或格式无效");
            Map<String,Object> payload=new LinkedHashMap<>();payload.put("sourceType",type);payload.put("sourceId",request.sourceId());payload.put("format",format);
            if(request.panelKey()!=null&&!request.panelKey().isBlank())payload.put("panelKey",request.panelKey().trim());
            if(request.revision()!=null)payload.put("revision",request.revision());
            Map<String,Object> safeInput=input(type,request.input());if(!safeInput.isEmpty())payload.put("input",safeInput);
            List<ExportTables.Sheet> sheets;
            try{sheets=load(payload,request.name()==null?"订阅报表":request.name());}
            catch(RuntimeException ex){throw ex;}
            catch(Exception ex){throw new IllegalArgumentException("报表数据生成失败，请检查当前权限和数据源",ex);}
            if(sheets.isEmpty())throw new IllegalArgumentException("没有可发送的数据");
            byte[] bytes=encode(sheets,format);if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("报表超过 8MB 限制");
            int rowCount=sheets.stream().mapToInt(ExportTables.Sheet::rowCount).sum();
            return new File(ExportTables.fileName(request.name()==null?"订阅报表":request.name(),format),contentType(format),bytes,rowCount);
        });
    }
    public void validateSubscription(BiActor actor,Submit request){
        asLive(actor,"bi:export:add",()->{
            if(request==null||request.sourceId()==null||request.sourceId()<1)throw new IllegalArgumentException("订阅资源无效");
            String type=request.sourceType()==null?"":request.sourceType().trim();String format=request.format()==null?"":request.format().trim().toLowerCase(Locale.ROOT);
            if(!Set.of("chart","dashboard").contains(type)||!Set.of("csv","xlsx","pdf").contains(format))throw new IllegalArgumentException("订阅来源或格式无效");
            if("chart".equals(type)){caps.require("bi:chart:query");charts.detail(request.sourceId());return null;}
            caps.require("bi:dashboard:consume");var view=dashboards.view(request.sourceId());List<Map<String,Object>> panels=dataPanels(view);
            String key=request.panelKey()==null?"":request.panelKey().trim();if(panels.isEmpty())throw new IllegalArgumentException("看板没有可导出的数据组件");
            if(!key.isEmpty()&&panels.stream().noneMatch(p->key.equals(String.valueOf(p.get("key")))))throw new IllegalArgumentException("数据组件不属于此看板或不能导出");
            if(Set.of("csv","pdf").contains(format)&&key.isEmpty()&&panels.size()>1)throw new IllegalArgumentException("多个数据组件请选择一个组件，或改用 Excel");
            return null;
        });
    }
    private Map<String,Object> submitCurrent(Submit request) {
        if (request == null || request.sourceId() == null || request.sourceId() < 1) throw new IllegalArgumentException("请指定已保存图表或已发布看板");
        String type = request.sourceType() == null ? "" : request.sourceType().trim();
        String format = request.format() == null ? "" : request.format().trim().toLowerCase(Locale.ROOT);
        if (!Set.of("chart","dashboard").contains(type) || !Set.of("csv","xlsx","pdf").contains(format))
            throw new IllegalArgumentException("仅支持导出已保存图表或已发布看板的 CSV / Excel / PDF");
        String panelKey = request.panelKey() == null || request.panelKey().isBlank() ? "" : request.panelKey().trim();
        if (!panelKey.isEmpty() && !panelKey.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("组件标识无效");
        if (request.name() != null && (request.name().isBlank() || request.name().trim().length() > 200)) throw new IllegalArgumentException("任务名称须为 1～200 字符");
        Map<String,Object> input = input(type, request.input());
        String name = request.name() == null ? null : request.name().trim();
        if ("chart".equals(type)) {
            caps.require("bi:chart:query");
            var detail = charts.detail(request.sourceId());
            if (name == null) name = detail.get("definition") instanceof ChartDefinitionService.Definition d && d.name() != null && !d.name().isBlank() ? d.name() : "图表";
            if (!panelKey.isEmpty() || request.revision() != null) throw new IllegalArgumentException("图表导出不接受组件或发布版本参数");
        } else {
            caps.require("bi:dashboard:consume");
            var view = dashboards.view(request.sourceId());
            if (request.revision() != null && !Objects.equals(request.revision(), view.get("revision")))
                throw new BiAccessException(409, "看板发布版本已改变，请刷新看板");
            List<Map<String,Object>> exportable = dataPanels(view);
            if (exportable.isEmpty()) throw new IllegalArgumentException("该看板没有可导出的数据组件");
            if (!panelKey.isEmpty() && exportable.stream().noneMatch(p -> panelKey.equals(String.valueOf(p.get("key")))))
                throw new IllegalArgumentException("组件不属于此看板的发布版本或不是数据组件");
            if (Set.of("csv","pdf").contains(format) && panelKey.isEmpty() && exportable.size() > 1)
                throw new IllegalArgumentException("多个数据组件请使用 Excel，或指定其中一个组件导出 CSV / PDF");
            if (name == null) name = String.valueOf(view.getOrDefault("name", "看板"));
        }
        Long owner = actors.currentActor().userId();
        if (mapper.active(owner) >= 3) throw new IllegalArgumentException("同时最多 3 个进行中的导出任务，请等待完成或取消后再提交");
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", type); payload.put("sourceId", request.sourceId()); payload.put("format", format);
        if (!panelKey.isEmpty()) payload.put("panelKey", panelKey);
        if (request.revision() != null) payload.put("revision", request.revision());
        if (!input.isEmpty()) payload.put("input", input);
        String encoded = json.writeValueAsString(payload);
        if (encoded.length() > 8192) throw new IllegalArgumentException("导出条件过长");
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("name", name); row.put("sourceType", type); row.put("sourceId", request.sourceId());
        row.put("panelKey", panelKey); row.put("format", format); row.put("requestJson", encoded);
        row.put("owner", owner); row.put("actor", actors.currentActor().username());
        mapper.insert(row);
        Long id = ((Number) row.get("id")).longValue();
        BiActor actor = actors.currentActor();
        executor.execute(() -> {try{asLive(actor,"bi:export:add",()->{run(id);return null;});}catch(RuntimeException ex){if(mapper.claim(id)==1)fail(id,"任务用户或权限已失效，请重新提交");}});
        Map<String,Object> created = new LinkedHashMap<>();
        created.put("id", id); created.put("status", "queued"); created.put("name", name);
        return created;
    }
    public Map<String,Object> cancel(Long id) {
        caps.require("bi:export:cancel");
        owned(id, false);
        if (mapper.cancel(id, actors.currentActor().userId()) != 1) throw new IllegalArgumentException("任务已结束，无法取消");
        return Map.of("cancelled", true);
    }
    public File download(Long id) {
        return asLive(actors.currentActor(),"bi:export:download",()->downloadCurrent(id));
    }
    private File downloadCurrent(Long id) {
        caps.require("bi:export:download");
        mapper.purge();
        var row = mapper.getFile(id);
        if (row == null) throw new IllegalArgumentException("导出任务不存在");
        if (!Objects.equals(owner(row), actors.currentActor().userId())) throw new BiAccessException(403, "没有该资源的操作权限");
        if (expired(row.get("expireTime"))) throw new IllegalArgumentException("导出文件已过期，请重新提交");
        if (!"succeeded".equals(String.valueOf(row.get("status")))) throw new IllegalArgumentException("任务尚未完成或已失败");
        byte[] content = (byte[]) row.get("content");
        if (content == null || content.length == 0) throw new IllegalArgumentException("导出文件不存在或已清理");
        String fileName = String.valueOf(row.getOrDefault("fileName", "export.bin"));
        String type = String.valueOf(row.getOrDefault("contentType", "application/octet-stream"));
        try {
            Map<String,Object> request=json.readValue(String.valueOf(row.get("requestJson")),Map.class);
            Object expected=request.get("validationHash");
            if(!(expected instanceof String) || !expected.equals(fingerprint(load(request,String.valueOf(row.get("name"))))))
                throw new BiAccessException(409,"数据、身份或权限范围已变化，请重新导出");
        }catch(BiAccessException ex){throw ex;}catch(Exception ex){throw new BiAccessException(403,"无法验证当前数据权限，请重新导出");}
        return new File(fileName, type, content,((Number)row.getOrDefault("rowCount",0)).intValue());
    }
    void run(Long id) {
        var row = mapper.get(id);
        if (row == null || mapper.claim(id) != 1) return;
        try {
            @SuppressWarnings("unchecked") Map<String,Object> request = json.readValue(String.valueOf(row.get("requestJson")), Map.class);
            List<ExportTables.Sheet> sheets = load(request, String.valueOf(row.getOrDefault("name","数据")));
            if (sheets.isEmpty()) throw new IllegalArgumentException("没有可导出的数据");
            String format = String.valueOf(request.get("format"));
            byte[] bytes = encode(sheets, format);
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("导出文件超过 8MB，请缩小查询范围");
            int rows = sheets.stream().mapToInt(ExportTables.Sheet::rowCount).sum();
            String fileName = ExportTables.fileName(String.valueOf(row.get("name")), format);
            Map<String,Object> done = new LinkedHashMap<>();
            done.put("id", id); done.put("status", "succeeded"); done.put("message", "");
            done.put("rowCount", rows); done.put("fileName", fileName);
            done.put("contentType", contentType(format));
            done.put("content", bytes);
            request.put("validationHash",fingerprint(sheets));done.put("validationJson",json.writeValueAsString(request));
            mapper.finish(done);
        } catch (BiAccessException ex) {
            fail(id, ex.getMessage() == null || ex.getMessage().isBlank() ? "没有访问权限，请联系管理员授权" : ex.getMessage());
        } catch (IllegalArgumentException ex) {
            fail(id, ex.getMessage() == null || ex.getMessage().isBlank() ? "导出条件无效" : ex.getMessage());
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(ExportJobService.class).warn("Export job {} failed", id, ex);
            fail(id, "导出失败，请检查数据源或权限");
        }
    }
    private <T> T asLive(BiActor reference,String capability,java.util.function.Supplier<T> task){
        var current=live.resolve(reference);current.require(capability);Object[] result=new Object[1];
        ActorScope.run(current.actor(),current.capabilities(),()->result[0]=task.get());return (T)result[0];
    }
    private String fingerprint(List<ExportTables.Sheet> sheets){
        var actor=actors.currentActor();var identity=new LinkedHashMap<String,Object>();identity.put("user",actor.userId());identity.put("organization",actor.organizationId());identity.put("roles",new TreeSet<>(actor.roleIds()));
        return SecurityDigest.hash(json.writeValueAsString(canonical(Map.of("identity",identity,"tables",sheets.stream().map(sheet->{Map<String,Object> table=new LinkedHashMap<>();table.put("name",sheet.name());table.put("columns",sheet.columns());table.put("rows",sheet.rows());if(sheet.grid()!=null)table.put("grid",Map.of("cells",sheet.grid().cells(),"merges",sheet.grid().merges(),"headerRows",sheet.grid().headerRows(),"frozenColumns",sheet.grid().frozenColumns(),"summaryRows",sheet.grid().summaryRows(),"summaryColumns",sheet.grid().summaryColumns()));return table;}).toList()))));
    }
    private Object canonical(Object value){if(value instanceof Map<?,?> map){Map<String,Object> sorted=new TreeMap<>();map.forEach((k,v)->sorted.put(String.valueOf(k),canonical(v)));return sorted;}if(value instanceof Collection<?> list)return list.stream().map(this::canonical).toList();return value;}
    private void fail(Long id, String message) {
        String text = message.length() > 500 ? message.substring(0, 500) : message;
        Map<String,Object> done = new LinkedHashMap<>();
        done.put("id", id); done.put("status", "failed"); done.put("message", text);
        done.put("rowCount", 0); done.put("fileName", ""); done.put("contentType", ""); done.put("content", null);
        mapper.finish(done);
    }
    @SuppressWarnings("unchecked")
    private List<ExportTables.Sheet> load(Map<String,Object> request, String jobName) throws Exception {
        String type = String.valueOf(request.get("sourceType"));
        Long sourceId = ((Number) request.get("sourceId")).longValue();
        String format = String.valueOf(request.get("format"));
        String panelKey = request.get("panelKey") == null ? "" : String.valueOf(request.get("panelKey"));
        Map<String,Object> input = request.get("input") instanceof Map<?,?> map ? new LinkedHashMap<>((Map<String,Object>) map) : Map.of();
        List<ExportTables.Sheet> sheets = new ArrayList<>();
        if ("chart".equals(type)) {
            caps.require("bi:chart:query");
            if("xlsx".equals(format)){
                var payload=charts.exportData(sourceId);var d=(ChartDefinitionService.Definition)payload.get("definition");
                sheets.add("pivot".equals(d.type())?PivotExport.sheet(jobName,d,unwrap(payload),payload.get("fieldLabels") instanceof Map<?,?> labels?(Map<String,String>)labels:Map.of()):sheet(jobName,payload,format));
            }else sheets.add(sheet(jobName, charts.data(sourceId), format));
            return sheets;
        }
        caps.require("bi:dashboard:consume");
        var view = dashboards.view(sourceId);
        Integer revision = request.get("revision") instanceof Number n ? n.intValue() : (Integer) view.get("revision");
        if (request.get("revision") instanceof Number n && !Objects.equals(n.intValue(), view.get("revision")))
            throw new BiAccessException(409, "看板发布版本已改变，请刷新看板");
        List<Map<String,Object>> panels = dataPanels(view);
        int index = 0;
        for (var panel : panels) {
            String key = String.valueOf(panel.get("key"));
            if (!panelKey.isEmpty() && !panelKey.equals(key)) continue;
            Map<String,Object> payload = dashboards.panelData(sourceId, key, revision, input.isEmpty() ? null : input);
            String name=ExportTables.sheetName(String.valueOf(panel.getOrDefault("title", key)), index++);
            var presentation=payload.get("presentation") instanceof Map<?,?> p?(Map<String,Object>)p:Map.<String,Object>of();
            if("xlsx".equals(format) && "pivot".equals(presentation.get("type"))){
                Map<String,Object> definition=new LinkedHashMap<>();definition.put("schemaVersion",1);definition.put("name",name);definition.put("type","pivot");definition.put("style",presentation.get("style"));definition.put("advanced",presentation.get("advanced"));
                definition.put("query",Map.of("dimensions",presentation.get("queryDimensions"),"metrics",presentation.get("queryMetrics")));
                var d=json.readValue(json.writeValueAsString(definition),ChartDefinitionService.Definition.class);
                sheets.add(PivotExport.sheet(name,d,unwrap(payload),presentation.get("fieldLabels") instanceof Map<?,?> labels?(Map<String,String>)labels:Map.of()));
            }else sheets.add(sheet(name, unwrap(payload), format));
        }
        if (Set.of("csv","pdf").contains(format) && sheets.size() > 1) throw new IllegalArgumentException("多个数据组件请使用 Excel");
        return sheets;
    }
    private static byte[] encode(List<ExportTables.Sheet> sheets, String format) {
        if ("csv".equals(format)) return ExportTables.csv(sheets.get(0));
        if ("pdf".equals(format)) return ExportTables.pdf(sheets.get(0));
        return ExportTables.xlsx(sheets);
    }
    private static String contentType(String format) {
        if ("csv".equals(format)) return "text/csv; charset=UTF-8";
        if ("pdf".equals(format)) return "application/pdf";
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> dataPanels(Map<String,Object> view) {
        Object raw = view.get("panels");
        if (!(raw instanceof List<?> list)) return List.of();
        List<Map<String,Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?,?> map)) continue;
            Map<String,Object> panel = (Map<String,Object>) map;
            Object type = panel.get("componentType");
            if (type != null && !Set.of("chart","kpi").contains(String.valueOf(type))) continue;
            result.add(panel);
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    static ExportTables.Sheet sheet(String name, Map<String,Object> result, String format) {
        Map<String,Object> data = unwrap(result);
        List<String> columns = data.get("columns") instanceof List<?> list
                ? list.stream().map(v -> v == null ? "" : String.valueOf(v)).toList() : List.of();
        List<Map<String,Object>> rows = data.get("rows") instanceof List<?> list ? (List<Map<String,Object>>) list : List.of();
        if (columns.size() > 64) throw new IllegalArgumentException("导出列数超过限制");
        int maxRows = "pdf".equals(format) ? ExportTables.MAX_ROWS_PDF
                : "csv".equals(format) ? ExportTables.MAX_ROWS_CSV : ExportTables.MAX_ROWS_XLSX;
        if (rows.size() > maxRows) throw new IllegalArgumentException("导出行数超过限制");
        if(data.get("columnLabels") instanceof Map<?,?> labels && !labels.isEmpty()){
            var headers=new ArrayList<String>();var seen=new HashSet<String>();for(String column:columns){String header=String.valueOf(labels.containsKey(column)?labels.get(column):column);while(!seen.add(header))header+="_";headers.add(header);}
            var renamed=new ArrayList<Map<String,Object>>();for(var row:rows){var copy=new LinkedHashMap<String,Object>();for(int i=0;i<columns.size();i++)copy.put(headers.get(i),row.get(columns.get(i)));renamed.add(copy);}return new ExportTables.Sheet(name,headers,renamed);
        }
        return new ExportTables.Sheet(name, columns, rows);
    }
    @SuppressWarnings("unchecked")
    static Map<String,Object> unwrap(Map<String,Object> result) {
        if (result == null) throw new IllegalArgumentException("导出结果无效");
        Map<String,Object> data = result.get("data") instanceof Map<?,?> inner ? new LinkedHashMap<>((Map<String,Object>) inner) : new LinkedHashMap<>(result);
        data.remove("executedSql"); data.remove("params"); data.remove("datasetName"); data.remove("presentation");
        return data;
    }
    private Map<String,Object> input(String type, Map<String,Object> raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        if ("chart".equals(type)) throw new IllegalArgumentException("图表导出不接受查询条件，请使用已保存定义");
        if (!Set.of("filters","links","drills").containsAll(raw.keySet())) throw new IllegalArgumentException("只允许提交筛选器、联动或下钻值");
        String encoded = json.writeValueAsString(raw);
        if (encoded.length() > 4096) throw new IllegalArgumentException("导出条件过长");
        return new LinkedHashMap<>(raw);
    }
    private Map<String,Object> owned(Long id, boolean file) {
        var row = file ? mapper.getFile(id) : mapper.get(id);
        if (row == null) throw new IllegalArgumentException("导出任务不存在");
        if (!Objects.equals(owner(row), actors.currentActor().userId())) throw new BiAccessException(403, "没有该资源的操作权限");
        return row;
    }
    private static Long owner(Map<String,Object> row) {
        Object value = row.get("ownerPrincipal");
        return value instanceof Number n ? n.longValue() : null;
    }
    private static boolean expired(Object value) {
        if (value == null) return true;
        if (value instanceof Date date) return !date.toInstant().isAfter(Instant.now());
        if (value instanceof java.time.LocalDateTime time) return !time.atZone(java.time.ZoneId.systemDefault()).toInstant().isAfter(Instant.now());
        return false;
    }
    private static Map<String,Object> publicJob(Map<String,Object> row) {
        Map<String,Object> item = new LinkedHashMap<>();
        for (String key : List.of("id","name","sourceType","sourceId","panelKey","format","status","message","rowCount","fileName","expireTime","createTime"))
            if (row.containsKey(key)) item.put(key, row.get(key));
        return item;
    }
}
