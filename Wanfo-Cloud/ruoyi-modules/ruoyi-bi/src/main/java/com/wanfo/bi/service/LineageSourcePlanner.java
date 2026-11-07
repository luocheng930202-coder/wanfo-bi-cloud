package com.wanfo.bi.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import com.wanfo.bi.domain.dataset.DatasetJoinModel;
import tools.jackson.databind.ObjectMapper;

/** Extracts declared dataset sources. Unresolved SQL is marked incomplete and is not guessed. */
public final class LineageSourcePlanner {
    public record Read(List<String> tables, List<String> joinTables, boolean incomplete) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private LineageSourcePlanner() {}

    public static Read read(String sqlText, String modelJson) {
        LinkedHashSet<String> tables = new LinkedHashSet<>();
        List<String> joins = new ArrayList<>();
        boolean incomplete = false;
        String modelSql = null;
        if (modelJson != null && !modelJson.isBlank()) {
            try {
                DatasetJoinModel model = JSON.readValue(modelJson, DatasetJoinModel.class);
                for (DatasetJoinModel.JoinStep step : model.normalizedJoins()) {
                    String name = normalize(step == null ? null : step.getRightTable());
                    if (name == null) incomplete = true;
                    else { joins.add(name); tables.add(name); }
                }
                modelSql = model.getSourceSql();
            } catch (RuntimeException ex) {
                incomplete = true;
            }
        }
        String sql = sqlText != null && !sqlText.isBlank() ? sqlText : modelSql;
        if (sql != null && !sql.isBlank()) {
            SqlRead extracted = extractSql(sql);
            tables.addAll(extracted.tables());
            incomplete = incomplete || extracted.incomplete();
        }
        return new Read(List.copyOf(tables), List.copyOf(joins), incomplete);
    }

    public static Long uniqueMatch(List<Long> ids) {
        return ids != null && ids.size() == 1 ? ids.get(0) : null;
    }

    static String normalize(String name) {
        if (name == null) return null;
        String value = name.trim();
        if (value.length() >= 2 && ((value.startsWith("`") && value.endsWith("`")) || (value.startsWith("\"") && value.endsWith("\""))))
            value = value.substring(1, value.length() - 1).trim();
        if (value.isEmpty()) return null;
        int dot = value.lastIndexOf('.');
        if (dot >= 0) value = value.substring(dot + 1).trim();
        return value.isEmpty() ? null : value;
    }

    private record SqlRead(List<String> tables, boolean incomplete) {}

    private static SqlRead extractSql(String sql) {
        String stripped = strip(sql);
        boolean incomplete = stripped.indexOf(';') >= 0;
        String lower = stripped.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> tables = new LinkedHashSet<>();
        int cursor = 0;
        while (cursor < stripped.length()) {
            int from = keyword(lower, cursor, "from");
            int join = keyword(lower, cursor, "join");
            int at = earliest(from, join);
            if (at < 0) break;
            boolean fromList = at == from;
            cursor = at + (fromList ? 4 : 4);
            boolean more = true;
            while (more) {
                cursor = skipWs(stripped, cursor);
                if (cursor >= stripped.length() || stripped.charAt(cursor) == '(') {
                    incomplete = true;
                    if (cursor < stripped.length()) cursor = skipParens(stripped, cursor);
                    more = false;
                    continue;
                }
                Ident ident = readIdent(stripped, cursor);
                if (ident == null || ident.name() == null) { incomplete = true; more = false; cursor = stripped.length(); continue; }
                cursor = ident.end();
                int after = skipWs(stripped, cursor);
                if (after < stripped.length() && stripped.charAt(after) == '(') {
                    incomplete = true;
                    cursor = skipParens(stripped, after);
                    more = false;
                    continue;
                }
                tables.add(ident.name());
                if (fromList && after < stripped.length() && stripped.charAt(after) == ',') { cursor = after + 1; continue; }
                more = false;
                cursor = after;
            }
        }
        return new SqlRead(List.copyOf(tables), incomplete);
    }

    private record Ident(String name, int end) {}

    private static Ident readIdent(String sql, int start) {
        if (start >= sql.length()) return null;
        char quote = sql.charAt(start);
        if (quote == '`' || quote == '"') {
            int end = sql.indexOf(quote, start + 1);
            if (end < 0) return new Ident(null, sql.length());
            return new Ident(normalize(sql.substring(start, end + 1)), end + 1);
        }
        int end = start;
        while (end < sql.length() && identChar(sql.charAt(end))) end++;
        if (end == start) return null;
        while (end < sql.length() && sql.charAt(end) == '.') {
            int next = end + 1;
            if (next < sql.length() && (sql.charAt(next) == '`' || sql.charAt(next) == '"')) {
                Ident quoted = readIdent(sql, next);
                if (quoted == null || quoted.name() == null) return new Ident(null, sql.length());
                return new Ident(quoted.name(), quoted.end());
            }
            int part = next;
            while (part < sql.length() && identChar(sql.charAt(part))) part++;
            if (part == next) break;
            end = part;
        }
        return new Ident(normalize(sql.substring(start, end)), end);
    }

    private static String strip(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                int n = sql.indexOf('\n', i);
                i = n < 0 ? sql.length() : n;
                continue;
            }
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int n = sql.indexOf("*/", i + 2);
                i = n < 0 ? sql.length() : n + 2;
                continue;
            }
            if (c == '\'' || c == '"') {
                char q = c;
                i++;
                while (i < sql.length()) {
                    if (sql.charAt(i) == q) { i++; if (i < sql.length() && sql.charAt(i) == q) { i++; continue; } break; }
                    if (sql.charAt(i) == '\\') i += Math.min(2, sql.length() - i);
                    else i++;
                }
                out.append(' ');
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static int keyword(String lower, int from, String word) {
        int at = from;
        while (at < lower.length()) {
            int found = lower.indexOf(word, at);
            if (found < 0) return -1;
            boolean left = found == 0 || !identChar(lower.charAt(found - 1));
            int end = found + word.length();
            boolean right = end >= lower.length() || !identChar(lower.charAt(end));
            if (left && right) return found;
            at = found + 1;
        }
        return -1;
    }

    private static int earliest(int left, int right) {
        if (left < 0) return right;
        if (right < 0) return left;
        return Math.min(left, right);
    }

    private static int skipWs(String sql, int i) {
        while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) i++;
        return i;
    }

    private static int skipParens(String sql, int i) {
        if (i >= sql.length() || sql.charAt(i) != '(') return i;
        int depth = 0;
        for (; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i + 1;
        }
        return sql.length();
    }

    private static boolean identChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
