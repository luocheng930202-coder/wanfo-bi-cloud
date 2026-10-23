package com.wanfo.bi.datasource.snapshot;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** A bounded, immutable rectangular snapshot. Identifiers with leading zeroes stay text. */
public record SnapshotData(List<Column> columns, List<List<String>> rows) {
    public static final int MAX_ROWS = 20000, MAX_COLUMNS = 200, MAX_CELL = 2000;
    public record Column(String name, String type) { }

    public SnapshotData {
        if (columns == null || columns.isEmpty() || columns.size() > MAX_COLUMNS || rows == null || rows.size() > MAX_ROWS)
            throw new IllegalArgumentException("数据须包含 1～200 列，且不超过 20000 行");
        Set<String> names = new HashSet<>();
        for (Column c : columns) {
            if (c.name() == null || c.name().isBlank() || c.name().length() > 120 || c.name().chars().anyMatch(Character::isISOControl)
                    || !names.add(c.name().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("字段名为空、重复或过长");
            if (!Set.of("VARCHAR", "DECIMAL", "BOOLEAN", "DATE", "TIMESTAMP").contains(c.type())) throw new IllegalArgumentException("字段类型无效");
        }
        columns = List.copyOf(columns);
        List<List<String>> safe = new ArrayList<>();
        for (List<String> row : rows) {
            if (row.size() != columns.size()) throw new IllegalArgumentException("数据行的列数与表头不一致");
            for (String cell : row) if (cell != null && cell.length() > MAX_CELL) throw new IllegalArgumentException("单元格超过 2000 字符");
            safe.add(Collections.unmodifiableList(new ArrayList<>(row)));
        }
        rows = List.copyOf(safe);
    }

    public static SnapshotData infer(List<String> names, List<List<String>> rows) {
        if(names==null || names.isEmpty() || names.size()>MAX_COLUMNS || rows==null || rows.isEmpty() || rows.size()>MAX_ROWS)throw new IllegalArgumentException("数据须包含 1～200 列、1～20000 行");
        List<Column> columns = new ArrayList<>();
        for (int i=0; i<names.size(); i++) {
            String type = null;
            for (List<String> row : rows) {
                if (row.size()!=names.size()) throw new IllegalArgumentException("数据行的列数与表头不一致");
                String value=row.get(i); if(value==null || value.isEmpty()) continue;
                String next=inferType(value); type=type==null?next:type.equals(next)?type:"VARCHAR";
            }
            columns.add(new Column(names.get(i),type==null?"VARCHAR":type));
        }
        return new SnapshotData(columns,rows);
    }

    private static String inferType(String value) {
        if(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) return "BOOLEAN";
        if(value.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) {
            BigDecimal number=new BigDecimal(value);
            if(number.precision()<=38 && number.scale()<=12 && number.precision()-number.scale()<=26) return "DECIMAL";
        }
        try { LocalDate.parse(value); return "DATE"; } catch (RuntimeException ignored) { }
        try { LocalDateTime.parse(value.replace(' ','T')); return "TIMESTAMP"; } catch (RuntimeException ignored) { }
        return "VARCHAR";
    }

    /** Refresh must preserve the saved dataset contract; values are checked against the old types. */
    public SnapshotData compatibleWith(SnapshotData old) {
        if(!columns.stream().map(Column::name).toList().equals(old.columns.stream().map(Column::name).toList()))
            throw new IllegalArgumentException("刷新字段或顺序变化，请创建新的数据源");
        SnapshotData result=new SnapshotData(old.columns,rows);
        for(List<String> row:rows) for(int i=0;i<columns.size();i++) result.value(i,row.get(i));
        return result;
    }

    public Object value(int index,String value) {
        if(value==null || value.isEmpty()) return null;
        try {
            return switch(columns.get(index).type()) {
                case "DECIMAL" -> {BigDecimal n=new BigDecimal(value); if(n.scale()>12 || n.precision()-n.scale()>26) throw new IllegalArgumentException(); yield n;}
                case "BOOLEAN" -> {if(!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))throw new IllegalArgumentException(); yield Boolean.valueOf(value);}
                case "DATE" -> Math.toIntExact(LocalDate.parse(value).toEpochDay());
                case "TIMESTAMP" -> LocalDateTime.parse(value.replace(' ','T')).toInstant(ZoneOffset.UTC).toEpochMilli();
                default -> value;
            };
        } catch(RuntimeException e) {throw new IllegalArgumentException("字段 “"+columns.get(index).name()+"” 数据类型变化，保留原快照");}
    }
}
