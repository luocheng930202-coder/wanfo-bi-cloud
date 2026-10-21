package com.wanfo.bi.query;

import java.util.Locale;
import java.util.Set;
import com.wanfo.bi.domain.query.QueryModel;

/** Calendar buckets are compiled by Calcite, after the secured input relation. */
public final class TimeGrain {
    private TimeGrain() { }
    private static final Set<String> ALLOWED = Set.of("day", "week", "month", "quarter", "year");

    public static String expression(QueryModel.Dimension dimension, String type) {
        String column = FilterTreeCompiler.quote(dimension.getField());
        String grain = dimension.getTimeGrain();
        if (grain == null) return column;
        if (!ALLOWED.contains(grain)) throw new IllegalArgumentException("时间粒度仅支持日、周、月、季度、年");
        if (type == null || !type.trim().toUpperCase(Locale.ROOT).matches("(?:DATE|DATETIME|TIMESTAMP)(?:\\(\\d+\\))?"))
            throw new IllegalArgumentException("时间粒度仅支持 DATE、DATETIME、TIMESTAMP 字段，请先转换文本日期");
        if (grain.equals("day")) return "CAST(" + column + " AS DATE)";
        // FLOOR WEEK differs between Calcite and its MySQL dialect. DAYOFWEEK uses Sunday=1 in both.
        if (grain.equals("week")) return "CAST(" + column + " AS DATE) - MOD(DAYOFWEEK(CAST(" + column + " AS DATE)) + 5, 7) * INTERVAL '1' DAY";
        // The MySQL adapter cannot unparse FLOOR TO QUARTER; derive it from the month start.
        if (grain.equals("quarter")) return "CAST(FLOOR(CAST(" + column + " AS TIMESTAMP) TO MONTH) AS DATE) - MOD(EXTRACT(MONTH FROM " + column + ") - 1, 3) * INTERVAL '1' MONTH";
        return "CAST(FLOOR(CAST(" + column + " AS TIMESTAMP) TO " + grain.toUpperCase(Locale.ROOT) + ") AS DATE)";
    }
}
