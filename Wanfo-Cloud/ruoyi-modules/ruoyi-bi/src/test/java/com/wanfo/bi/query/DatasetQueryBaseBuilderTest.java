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
    private static BiDatasetFieldDO field(String name,String ext,String formula,int order){var f=new BiDatasetFieldDO();f.setOriginName(name);f.setExtFlag(ext);f.setExtFormula(formula);f.setOrderNum(order);return f;}
}
