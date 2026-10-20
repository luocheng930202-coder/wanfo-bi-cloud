package com.wanfo.bi.query;
import java.util.ArrayList;
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
        Set<String> allowed = physical.stream().map(BiDatasetFieldDO::getOriginName).collect(Collectors.toSet());
        List<BiDatasetFieldDO> calcs = fields.stream().filter(f -> "1".equals(f.getExtFlag())).toList();
        if (calcs.isEmpty()) return unionSql;
        StringBuilder sb = new StringBuilder("SELECT bi_inner.*");
        for (BiDatasetFieldDO c : calcs) {
            sb.append(", (").append(CalcFormulaCompiler.compile(c.getExtFormula(), allowed,quote)).append(") AS ")
                    .append(quote).append(c.getOriginName().replace(quote,quote+quote)).append(quote);
        }
        sb.append(" FROM (").append(unionSql).append(") AS bi_inner");
        return sb.toString();
    }
}
