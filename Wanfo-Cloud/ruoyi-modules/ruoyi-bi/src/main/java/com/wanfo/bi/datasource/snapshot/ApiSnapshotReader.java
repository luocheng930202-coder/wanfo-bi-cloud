package com.wanfo.bi.datasource.snapshot;
import java.util.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.*;

/** Bounded page-number pagination. A partial or changing response never replaces a snapshot. */
public final class ApiSnapshotReader {
    private ApiSnapshotReader(){}
    public static SnapshotData read(ApiSnapshotClient client,ApiSnapshotClient.Config config,ObjectMapper json)throws Exception {
        var p=config.pagination();
        if(p==null)return SnapshotParser.json(client.fetch(config),config.dataPath(),json,false,config.flatten(),config.arrayPaths());
        boolean cursorMode=p.cursorParam()!=null&&!p.cursorParam().isBlank();
        if(cursorMode){if(!p.cursorParam().matches("[A-Za-z][A-Za-z0-9_]{0,31}")||p.sizeParam()==null||!p.sizeParam().matches("[A-Za-z][A-Za-z0-9_]{0,31}")||p.cursorParam().equals(p.sizeParam())||p.nextCursorPath()==null||p.nextCursorPath().isBlank()||p.nextCursorPath().length()>160)throw new IllegalArgumentException("游标分页参数无效");at(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(),p.nextCursorPath());if(p.initialCursor()!=null&&p.initialCursor().length()>2048)throw new IllegalArgumentException("初始游标过长");}
        else if(p.pageParam()==null || !p.pageParam().matches("[A-Za-z][A-Za-z0-9_]{0,31}") || p.sizeParam()==null || !p.sizeParam().matches("[A-Za-z][A-Za-z0-9_]{0,31}") || p.pageParam().equals(p.sizeParam()))throw new IllegalArgumentException("分页参数无效");
        if(p.pageSize()<1 || p.pageSize()>1000 || p.startPage()<0 || p.startPage()>1 || p.maxPages()<1 || p.maxPages()>100)throw new IllegalArgumentException("分页参数无效：每页 1～1000 行，最多 100 页，起始页为 0 或 1");
        URI uri=URI.create(config.url());
        if(!"POST".equalsIgnoreCase(config.method()) && uri.getRawQuery()!=null)for(String pair:uri.getRawQuery().split("&")){
            String key=URLDecoder.decode(pair.split("=",2)[0],StandardCharsets.UTF_8);
            if(cursorMode?(key.equals(p.cursorParam())||key.equals(p.sizeParam())):(key.equals(p.pageParam()) || key.equals(p.sizeParam())))throw new IllegalArgumentException("请从 API 地址移除已配置的分页参数");
        }
        long deadline=System.nanoTime()+30_000_000_000L;int bytes=0;Long total=null;
        SnapshotData first=null;List<List<String>> rows=new ArrayList<>();Set<String> pages=new HashSet<>(),usedCursors=new HashSet<>();String cursor=p.initialCursor();if(cursor!=null&&!cursor.isBlank()){if(cursor.length()>2048)throw new IllegalArgumentException("初始游标过长");usedCursors.add(cursor);}
        for(int index=0;index<p.maxPages();index++){
            if(System.nanoTime()>deadline)throw new IllegalArgumentException("API 分页读取超过 30 秒，保留原快照");
            String url=config.url();Map<String,Object> params=new LinkedHashMap<>(config.body()==null?Map.of():config.body());
            if(cursorMode){if(cursor!=null&&!cursor.isBlank()){if("POST".equalsIgnoreCase(config.method()))params.put(p.cursorParam(),cursor);else url=url+(url.contains("?")?"&":"?")+p.cursorParam()+"="+URLEncoder.encode(cursor,StandardCharsets.UTF_8);}if("POST".equalsIgnoreCase(config.method()))params.put(p.sizeParam(),p.pageSize());else url=url+(url.contains("?")?"&":"?")+p.sizeParam()+"="+p.pageSize();}
            else if("POST".equalsIgnoreCase(config.method())){params.put(p.pageParam(),p.startPage()+index);params.put(p.sizeParam(),p.pageSize());}
            else url=url+(url.contains("?")?"&":"?")+p.pageParam()+"="+(p.startPage()+index)+"&"+p.sizeParam()+"="+p.pageSize();
            byte[] body=client.fetch(new ApiSnapshotClient.Config(url,config.dataPath(),config.headers(),null,config.method(),params,config.flatten(),config.oauth(),config.expandArrayPath(),config.expandArrayPaths()),deadline);bytes+=body.length;
            if(bytes>SnapshotParser.MAX_BYTES || System.nanoTime()>deadline)throw new IllegalArgumentException("API 分页累计超过 5 MB 或 30 秒");
            JsonNode root=SnapshotParser.tree(body,json),array=at(root,config.dataPath());
            if(!array.isArray())throw new IllegalArgumentException("API 数据路径须指向对象数组");
            String nextCursor=null;if(cursorMode){JsonNode next=at(root,p.nextCursorPath());if(!next.isMissingNode()&&!next.isNull()){if(!next.isValueNode())throw new IllegalArgumentException("下一游标须为字符串或数字");if(!next.asText().isBlank()){nextCursor=next.asText();if(nextCursor.length()>2048)throw new IllegalArgumentException("API 下一游标过长");}}}
            String totalPath=p.totalPath();
            if(totalPath==null || totalPath.isBlank()){
                String parent=config.dataPath()==null?"":config.dataPath().replaceFirst("[^.]+$","");
                for(String candidate:List.of(parent+"total",parent+"totalCount","total","totalCount"))if(!at(root,candidate).isMissingNode()){totalPath=candidate;break;}
            }
            if(totalPath!=null && !totalPath.isBlank()){
                JsonNode count=at(root,totalPath);
                if(!count.isIntegralNumber() || !count.canConvertToLong() || count.longValue()<0 || count.longValue()>SnapshotData.MAX_ROWS)throw new IllegalArgumentException("API 总行数无效或超过 20000 行");
                if(total!=null && total.longValue()!=count.longValue())throw new IllegalArgumentException("分页过程中总行数变化，请重试");total=count.longValue();
            }
            if(array.isEmpty()){
                if(cursorMode&&first!=null){if(nextCursor==null)return new SnapshotData(first.columns(),rows);if(!usedCursors.add(nextCursor))throw new IllegalArgumentException("API 返回重复游标，请检查分页配置");cursor=nextCursor;continue;}
                if(first==null || total!=null && total!=rows.size())throw new IllegalArgumentException("接口提前返回空页，无法确认完整数据");
                return new SnapshotData(first.columns(),rows);
            }
            if(!pages.add(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(array.toString().getBytes(StandardCharsets.UTF_8)))))throw new IllegalArgumentException("API 返回重复分页，请检查页码参数");
            var page=SnapshotParser.json(body,config.dataPath(),json,true,config.flatten(),config.arrayPaths());
            if(first==null)first=page;else page=page.compatibleWith(first);
            rows.addAll(page.rows());
            if(rows.size()>SnapshotData.MAX_ROWS || total!=null && rows.size()>total)throw new IllegalArgumentException("分页行数超过允许范围或接口声明总数");
            if(cursorMode){if(nextCursor==null)return new SnapshotData(first.columns(),rows);if(!usedCursors.add(nextCursor))throw new IllegalArgumentException("API 返回重复游标，请检查分页配置");cursor=nextCursor;continue;}
            if(total!=null?rows.size()==total:page.rows().size()<p.pageSize())return new SnapshotData(first.columns(),rows);
        }
        throw new IllegalArgumentException("达到最大页数仍未读取完整数据，保留原快照");
    }
    private static JsonNode at(JsonNode node,String path){
        if(path==null || path.isBlank())return node;
        if(!path.matches("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*"))throw new IllegalArgumentException("JSON 路径须为 data.rows 形式");
        for(String key:path.split("\\."))node=node.path(key);return node;
    }
}
