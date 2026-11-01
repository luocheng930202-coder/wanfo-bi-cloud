package com.wanfo.bi.query;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.wanfo.bi.domain.BiDatasetFieldDO;
/** Expands stored union SQL with calculated field projections. */
public final class DatasetQueryBaseBuilder {
    private DatasetQueryBaseBuilder() { }
    public static String expand(String unionSql, List<BiDatasetFieldDO> fields) {
        return expand(unionSql,fields,"\"");
    }
    public static String expand(String unionSql, List<BiDatasetFieldDO> fields,String quote) {
        List<BiDatasetFieldDO> physical = fields.stream().filter(f -> !"1".equals(f.getExtFlag())).toList();
        Set<String> allowed = physical.stream().map(BiDatasetFieldDO::getOriginName).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        List<BiDatasetFieldDO> calcs = fields.stream().filter(f -> "1".equals(f.getExtFlag())).toList();
        if (calcs.isEmpty()) return unionSql;
        String currentSql=unionSql;
        for (BiDatasetFieldDO c : calcs) {
            String expression=CalcFormulaCompiler.compile(c.getExtFormula(),allowed,quote);
            currentSql="SELECT bi_inner.*, ("+expression+") AS "+quote+c.getOriginName().replace(quote,quote+quote)+quote+" FROM ("+currentSql+") AS bi_inner";
            allowed.add(c.getOriginName());
        }
        return currentSql;
    }

    /**
     * Rewrites legacy MySQL-dialect dataset SQL into Calcite MYSQL_ANSI form.
     * Backtick identifiers become double-quoted; physical catalog is remapped to the
     * registered Calcite schema alias when they differ.
     */
    public static String forCalcite(String sql, String schemaName, String sourceCatalog) {
        if (sql == null || sql.isBlank()) return sql;
        String out = rewriteBackticks(sql);
        if (schemaName != null && !schemaName.isBlank()
                && sourceCatalog != null && !sourceCatalog.isBlank()
                && !schemaName.equals(sourceCatalog)) {
            String from = "\"" + sourceCatalog.replace("\"", "\"\"") + "\".";
            String to = "\"" + schemaName.replace("\"", "\"\"") + "\".";
            out = out.replace(from, to);
        }
        return out;
    }

    private static String rewriteBackticks(String sql) {
        StringBuilder sb = new StringBuilder(sql.length());
        for (int i = 0; i < sql.length(); ) {
            char c = sql.charAt(i);
            if (c != '`') { sb.append(c); i++; continue; }
            i++;
            StringBuilder id = new StringBuilder();
            while (i < sql.length()) {
                char ch = sql.charAt(i);
                if (ch == '`') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '`') { id.append('`'); i += 2; continue; }
                    i++;
                    break;
                }
                id.append(ch);
                i++;
            }
            sb.append('"').append(id.toString().replace("\"", "\"\"")).append('"');
        }
        return sb.toString();
    }
}
