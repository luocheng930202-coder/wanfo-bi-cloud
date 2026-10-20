package com.wanfo.bi.query;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.wanfo.bi.domain.form.DataFormField;

/** Builds a single-row INSERT for approved form fields only. */
public final class DataFormInsertBuilder
{
    private static final Set<String> WIDGETS = Set.of("text", "number", "date", "textarea");

    private DataFormInsertBuilder() { }

    public record Plan(String sql, List<Object> params, Map<String, Object> normalized) { }

    public static Plan compile(String catalog, String table, String quoteChar,
                               List<DataFormField> fields, Map<String, String> types,
                               Map<String, Object> rawValues)
    {
        if (fields == null || fields.isEmpty() || fields.size() > 50)
        {
            throw new IllegalArgumentException("填报字段配置无效");
        }
        if (catalog == null || catalog.isBlank() || table == null || table.isBlank())
        {
            throw new IllegalArgumentException("目标表无效");
        }
        String q = quoteChar == null || quoteChar.isBlank() ? "\"" : quoteChar.trim();
        Map<String, Object> normalized = new LinkedHashMap<>();
        List<String> columns = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (DataFormField field : fields)
        {
            if (field == null || field.getField() == null || field.getField().isBlank())
            {
                throw new IllegalArgumentException("字段名无效");
            }
            String name = field.getField();
            if (!types.containsKey(name))
            {
                throw new IllegalArgumentException("字段不在数据集内: " + name);
            }
            String widget = field.getWidget() == null ? "text" : field.getWidget().trim().toLowerCase(Locale.ROOT);
            if (!WIDGETS.contains(widget))
            {
                throw new IllegalArgumentException("不支持的控件类型: " + widget);
            }
            Object raw = rawValues == null ? null : rawValues.get(name);
            boolean required = Boolean.TRUE.equals(field.getRequired());
            if (raw == null || (raw instanceof String && ((String) raw).isBlank()))
            {
                if (required)
                {
                    throw new IllegalArgumentException("请填写: " + (field.getLabel() == null ? name : field.getLabel()));
                }
                continue;
            }
            Object value = coerce(raw, types.get(name), widget);
            normalized.put(name, value);
            columns.add(quote(q, name));
            params.add(value);
        }
        if (columns.isEmpty())
        {
            throw new IllegalArgumentException("请至少填写一个字段");
        }
        String placeholders = String.join(", ", columns.stream().map(c -> "?").toList());
        String sql = "INSERT INTO " + quote(q, catalog) + "." + quote(q, table)
                + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";
        return new Plan(sql, params, normalized);
    }

    private static Object coerce(Object raw, String jdbcType, String widget)
    {
        String type = jdbcType == null ? "" : jdbcType.toLowerCase(Locale.ROOT);
        if ("number".equals(widget) || FilterTreeCompiler.numeric(type))
        {
            if (raw instanceof Number)
            {
                return raw;
            }
            String text = String.valueOf(raw).trim();
            if (text.isEmpty())
            {
                throw new IllegalArgumentException("数值格式无效");
            }
            try
            {
                return new BigDecimal(text);
            }
            catch (NumberFormatException ex)
            {
                throw new IllegalArgumentException("数值格式无效: " + text);
            }
        }
        String text = String.valueOf(raw).trim();
        if (text.length() > 4000)
        {
            throw new IllegalArgumentException("文本过长");
        }
        return text;
    }

    private static String quote(String q, String identifier)
    {
        return q + identifier.replace(q, q + q) + q;
    }
}
