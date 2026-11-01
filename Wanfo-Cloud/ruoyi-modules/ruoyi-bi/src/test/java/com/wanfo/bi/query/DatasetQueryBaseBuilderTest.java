package com.wanfo.bi.query;

import com.wanfo.bi.domain.BiDatasetFieldDO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DatasetQueryBaseBuilderTest {
    @Test void calculatedFieldsRunInOrderAndCanReferenceEarlierSteps(){
        var amount=field("amount","0","",0);var trimmed=field("step_trim","1","TRIM([amount])",1);var normalized=field("step_normalized","1","UPPER([step_trim])",2);
        String sql=DatasetQueryBaseBuilder.expand("SELECT amount FROM orders",List.of(amount,trimmed,normalized));
        assertTrue(sql.toUpperCase().contains("TRIM("),sql);assertTrue(sql.toUpperCase().contains("UPPER("),sql);assertTrue(sql.contains("\"step_trim\""),sql);
    }

    @Test void forCalciteRewritesMySqlBackticksAndPhysicalCatalog(){
        String legacy="SELECT t0.`qty`, t1.`store_name` AS `r_store_name` FROM `bi_phase_a_test`.`fact_order` t0 "
                +"LEFT JOIN `bi_phase_a_test`.`dim_store` t1 ON t0.`store_id` = t1.`store_id`";
        String sql=DatasetQueryBaseBuilder.forCalcite(legacy,"phase_a_mysql","bi_phase_a_test");
        assertFalse(sql.contains("`"),sql);
        assertTrue(sql.contains("\"phase_a_mysql\".\"fact_order\""),sql);
        assertTrue(sql.contains("\"phase_a_mysql\".\"dim_store\""),sql);
        assertTrue(sql.contains("AS \"r_store_name\""),sql);
        assertFalse(sql.contains("bi_phase_a_test"),sql);
    }

    @Test void forCalcitePreservesEscapedBackticksInsideIdentifiers(){
        assertEquals("SELECT \"a`b\" FROM \"t\"",
                DatasetQueryBaseBuilder.forCalcite("SELECT `a``b` FROM `t`","s","s"));
    }

    private static BiDatasetFieldDO field(String name,String ext,String formula,int order){var f=new BiDatasetFieldDO();f.setOriginName(name);f.setExtFlag(ext);f.setExtFormula(formula);f.setOrderNum(order);return f;}
}
