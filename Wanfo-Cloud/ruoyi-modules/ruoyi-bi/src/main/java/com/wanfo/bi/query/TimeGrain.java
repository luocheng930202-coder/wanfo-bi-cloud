package com.wanfo.bi.query;

import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.wanfo.bi.domain.query.QueryModel;

/** Calendar buckets are compiled by Calcite, after the secured input relation. */
public final class TimeGrain {
    private TimeGrain() { }
    private static final Set<String> ALLOWED = Set.of("day", "week", "month", "quarter", "year");

    public static List<Map<String,Object>> calendarRows(QueryModel model,List<Map<String,Object>> rows) {
        var keys=model.getDimensions().stream().filter(d->d.getTimeGrain()!=null)
                .map(d->d.getAlias()==null || d.getAlias().isBlank()?d.getField():d.getAlias()).toList();
        if(keys.isEmpty())return rows;
        return rows.stream().map(row->{
            Map<String,Object> copy=new LinkedHashMap<>(row);
            for(String key:keys)if(copy.get(key) instanceof java.sql.Date date)copy.put(key,date.toLocalDate().toString());
            return copy;
        }).toList();
    }

    public static String expression(QueryModel.Dimension dimension, String type) {
        String column = FilterTreeCompiler.quote(dimension.getField());
        String grain = dimension.getTimeGrain();
        if (grain == null) return column;
        if (!ALLOWED.contains(grain)) throw new IllegalArgumentException("时间粒度仅支持日、周、月、季度、年");
        if (type == null || !type.trim().toUpperCase(Locale.ROOT).matches("(?:DATE|DATETIME|TIMESTAMP)(?:\\(\\d+\\))?"))
            throw new IllegalArgumentException("时间粒度仅支持 DATE、DATETIME、TIMESTAMP 字段，请先转换文本日期");
        if (grain.equals("day")) return "CAST(" + column + " AS DATE)";
        // FLOOR WEEK differs between Calcite and its MySQL dialect. DAYOFWEEK uses Sunday=1 in both.
        if (grain.equals("week")) return "CAST(TIMESTAMPADD(DAY, -MOD(DAYOFWEEK(CAST(" + column + " AS DATE)) + 5, 7), CAST(" + column + " AS TIMESTAMP)) AS DATE)";
        // The MySQL adapter cannot unparse FLOOR TO QUARTER; derive it from the month start.
        if (grain.equals("quarter")) return "CAST(TIMESTAMPADD(MONTH, -MOD(EXTRACT(MONTH FROM " + column + ") - 1, 3), FLOOR(CAST(" + column + " AS TIMESTAMP) TO MONTH)) AS DATE)";
        return "CAST(FLOOR(CAST(" + column + " AS TIMESTAMP) TO " + grain.toUpperCase(Locale.ROOT) + ") AS DATE)";
    }
}
