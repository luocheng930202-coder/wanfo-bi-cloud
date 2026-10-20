package com.wanfo.bi.query;

import java.util.*;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalcFieldTypesTest {
    BiDatasetFieldDO field(String name,String type){var f=new BiDatasetFieldDO();f.setOriginName(name);f.setOriginType(type);f.setExtFlag("0");return f;}
    final List<BiDatasetFieldDO> fields=List.of(field("amount","DECIMAL(14,2)"),field("qty","INT"),field("name","VARCHAR"),field("day","DATE"));
    @Test void numericExpressionsAreNumericForAggregation(){assertTrue(FilterTreeCompiler.numeric(CalcFieldTypes.infer("amount / NULLIF(qty,0)",fields)));}
    @Test void stringsAreNotNumeric(){assertTrue(CalcFieldTypes.infer("UPPER(name)",fields).startsWith("VARCHAR"));assertFalse(FilterTreeCompiler.numeric(CalcFieldTypes.infer("CONCAT(name,'x')",fields)));}
    @Test void dateReferencePreservesDateType(){assertEquals("DATE",CalcFieldTypes.infer("[day]",fields));}
    @Test void nullableCasePreservesNumericType(){assertTrue(FilterTreeCompiler.numeric(CalcFieldTypes.infer("CASE WHEN qty=0 THEN NULL ELSE amount/qty END",fields)));}
    @Test void invalidFunctionArgumentsAreRejected(){assertThrows(IllegalArgumentException.class,()->CalcFieldTypes.infer("UPPER(day)",fields));}
}
