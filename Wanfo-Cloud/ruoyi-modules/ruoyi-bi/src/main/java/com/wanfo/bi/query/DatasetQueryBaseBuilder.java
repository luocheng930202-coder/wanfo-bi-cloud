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
}
