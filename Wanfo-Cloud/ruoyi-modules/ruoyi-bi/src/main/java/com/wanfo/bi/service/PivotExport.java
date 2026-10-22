package com.wanfo.bi.service;

import java.math.BigDecimal;
import java.util.*;

/** An expanded Excel pivot layout. All numbers come from the query, never from adding visible cells. */
final class PivotExport {
    private PivotExport() { }
    private record Axis(List<Object> path,boolean total) { }
    private record Key(List<Object> row,List<Object> column) { }
    private static String metric(com.wanfo.bi.domain.query.QueryModel.Metric m){return m.getAlias()==null || m.getAlias().isBlank()?m.getAgg().toLowerCase(Locale.ROOT)+"_"+m.getField():m.getAlias();}
    private static List<Object> path(Map<String,Object> row,List<String> fields){return fields.stream().map(row::get).toList();}
    private static String label(Object value){return value==null?"（空值）":String.valueOf(value);}
    private static String axisLabel(Axis axis,int depth){return depth<axis.path().size()?label(axis.path().get(depth)):depth==axis.path().size()?(axis.path().isEmpty()?"筛选范围总计":"筛选范围小计"):"";}
    private static Object number(Object value){
        if(value==null)return null;
        try{return new BigDecimal(String.valueOf(value));}catch(NumberFormatException ex){throw new IllegalArgumentException("透视指标不是有效数值，请重新查询");}
    }
    private static void axes(List<Axis> out,List<List<Object>> leaves,List<Object> prefix,int depth,int size,boolean subtotals,boolean first){
        if(depth==size){out.add(new Axis(prefix,false));return;}
        if(depth>0 && subtotals && first)out.add(new Axis(prefix,true));
        Set<Object> children=new LinkedHashSet<>();for(var leaf:leaves)if(leaf.subList(0,depth).equals(prefix))children.add(leaf.get(depth));
        for(var child:children){var next=new ArrayList<>(prefix);next.add(child);axes(out,leaves,next,depth+1,size,subtotals,first);}
        if(depth>0 && subtotals && !first)out.add(new Axis(prefix,true));
    }
    private static List<Axis> axes(List<List<Object>> leaves,int size,boolean totals,boolean subtotals,boolean first){
        var out=new ArrayList<Axis>();if(leaves.isEmpty())return out;
        if(totals && first)out.add(new Axis(List.of(),true));
        axes(out,leaves,List.of(),0,size,subtotals,first);
        if(totals && !first)out.add(new Axis(List.of(),true));return out;
    }
    @SuppressWarnings("unchecked")
    static ExportTables.Sheet sheet(String name,ChartDefinitionService.Definition d,Map<String,Object> data,Map<String,String> labels){
        var q=d.query();var advanced=d.advanced();var style=d.style();
        Map<String,String> output=new HashMap<>();q.getDimensions().forEach(f->output.put(f.getField(),f.getAlias()==null || f.getAlias().isBlank()?f.getField():f.getAlias()));
        var rows=advanced.getPivotRowFields().stream().map(output::get).toList();var cols=advanced.getPivotColFields().stream().map(output::get).toList();var metrics=q.getMetrics().stream().map(PivotExport::metric).toList();
        boolean tree=style!=null && "tree".equals(style.getPivotHierarchy());
        boolean rowTotal=style!=null && Boolean.TRUE.equals(style.getPivotRowTotals()),colTotal=style!=null && Boolean.TRUE.equals(style.getPivotColTotals());
        boolean rowSub=tree || style!=null && Boolean.TRUE.equals(style.getPivotRowSubtotals()),colSub=style!=null && Boolean.TRUE.equals(style.getPivotColSubtotals());
        boolean first=style!=null && Boolean.TRUE.equals(style.getPivotTotalsFirst()),inCols=style==null || !Boolean.FALSE.equals(style.getPivotValuesInCols());
        Map<String,Object> totals=data.get("pivotTotals") instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();
        boolean requested=rowTotal || colTotal || rowSub || colSub;
        boolean ready="complete".equals(totals.get("status")) && rows.equals(totals.get("rowFields")) && cols.equals(totals.get("colFields")) && metrics.equals(totals.get("metricKeys"));
        if(requested && !ready)throw new IllegalArgumentException(String.valueOf(totals.getOrDefault("reason","透视汇总不可用或配置已变化，请更新数据后重试")));
        List<Map<String,Object>> detail=data.get("rows") instanceof List<?> list?(List<Map<String,Object>>)list:List.of();
        var rowPaths=new ArrayList<List<Object>>();var colPaths=new ArrayList<List<Object>>();Map<Key,Map<String,Object>> values=new HashMap<>();
        for(var leaf:detail){var r=path(leaf,rows);var c=path(leaf,cols);if(values.put(new Key(r,c),leaf)!=null)throw new IllegalArgumentException("透视单元格存在重复分组");if(!rowPaths.contains(r))rowPaths.add(r);if(!colPaths.contains(c))colPaths.add(c);}
        if(ready)for(var cell:(List<Map<String,Object>>)totals.get("cells")){
            int rd=((Number)cell.get("rowDepth")).intValue(),cd=((Number)cell.get("colDepth")).intValue();
            var dims=(Map<String,Object>)cell.get("dimensions");values.put(new Key(path(dims,rows.subList(0,rd)),path(dims,cols.subList(0,cd))),(Map<String,Object>)cell.get("values"));
        }
        var ra=axes(rowPaths,rows.size(),rowTotal,rowSub,first);var ca=axes(colPaths,cols.size(),colTotal,colSub,first);
        int left=rows.size()+(inCols?0:1),width=left+ca.size()*(inCols?metrics.size():1),headerRows=1+cols.size()+(inCols?1:0);
        if(width>256 || headerRows+ra.size()*(inCols?1:metrics.size())>ExportTables.MAX_ROWS_XLSX || (long)width*(headerRows+ra.size()*(inCols?1:metrics.size()))>200_000)throw new IllegalArgumentException("透视导出超过 10000 行、256 列或 20 万单元格，请缩小范围");
        width=Math.max(width,left);var grid=new ArrayList<List<Object>>();var merges=new ArrayList<ExportTables.Merge>();var summaries=new ArrayList<Integer>();
        for(int r=0;r<headerRows;r++)grid.add(new ArrayList<>(Collections.nCopies(width,null)));
        grid.get(0).set(0,"汇总口径：当前筛选和权限范围内全部数据；"+(Boolean.TRUE.equals(totals.get("detailTruncated"))?"明细已截断，总计仍包含未展示分组。":"明细为当前查询分组。")+" 树形层级按展开状态导出；高精度数值以文本保真。");
        if(width>1)merges.add(new ExportTables.Merge(0,0,0,width-1));
        for(int c=0;c<left;c++){grid.get(1).set(c,c<rows.size()?labels.getOrDefault(rows.get(c),rows.get(c)):"指标");if(headerRows>2)merges.add(new ExportTables.Merge(1,headerRows-1,c,c));}
        for(int a=0;a<ca.size();a++)for(int m=0;m<(inCols?metrics.size():1);m++){
            int c=left+a*(inCols?metrics.size():1)+m;
            for(int level=0;level<cols.size();level++)grid.get(1+level).set(c,axisLabel(ca.get(a),level));
            if(inCols)grid.get(headerRows-1).set(c,labels.getOrDefault(metrics.get(m),metrics.get(m)));
        }
        // Merge only identical hierarchical prefixes, never unrelated equal child labels.
        for(int level=0;level<cols.size();level++){
            int start=0;
            while(start<ca.size()){
                int end=start+1;var axis=ca.get(start);int prefix=Math.min(level+1,axis.path().size());
                while(end<ca.size() && axis.total()==ca.get(end).total() && axis.path().size()>level && ca.get(end).path().size()>level && axis.path().subList(0,prefix).equals(ca.get(end).path().subList(0,prefix)))end++;
                int c1=left+start*(inCols?metrics.size():1),c2=left+end*(inCols?metrics.size():1)-1;
                if(c2>c1)merges.add(new ExportTables.Merge(1+level,1+level,c1,c2));start=end;
            }
        }
        for(var axis:ra)for(int m=0;m<(inCols?1:metrics.size());m++){
            var row=new ArrayList<Object>(Collections.nCopies(width,null));for(int c=0;c<rows.size();c++)row.set(c,axisLabel(axis,c));
            if(!inCols)row.set(left-1,labels.getOrDefault(metrics.get(m),metrics.get(m)));
            for(int a=0;a<ca.size();a++){
                var value=values.get(new Key(axis.path(),ca.get(a).path()));
                for(int k=0;k<(inCols?metrics.size():1);k++)row.set(left+a*(inCols?metrics.size():1)+k,value==null?null:number(value.get(metrics.get(inCols?k:m))));
            }
            if(axis.total())summaries.add(grid.size());grid.add(row);
        }
        for(int level=0;level<rows.size();level++){
            int start=0,multiplier=inCols?1:metrics.size();
            while(start<ra.size()){
                int end=start+1;var axis=ra.get(start);
                while(end<ra.size() && axis.path().size()>level && ra.get(end).path().size()>level && axis.path().subList(0,level+1).equals(ra.get(end).path().subList(0,level+1)))end++;
                int r1=headerRows+start*multiplier,r2=headerRows+end*multiplier-1;
                if(r2>r1 && axis.path().size()>level)merges.add(new ExportTables.Merge(r1,r2,level,level));start=end;
            }
        }
        var summaryColumns=new ArrayList<Integer>();for(int a=0;a<ca.size();a++)if(ca.get(a).total())for(int m=0;m<(inCols?metrics.size():1);m++)summaryColumns.add(left+a*(inCols?metrics.size():1)+m);
        return new ExportTables.Sheet(name,List.of(),List.of(),new ExportTables.Grid(grid,merges,headerRows,left,summaries,summaryColumns));
    }
}
