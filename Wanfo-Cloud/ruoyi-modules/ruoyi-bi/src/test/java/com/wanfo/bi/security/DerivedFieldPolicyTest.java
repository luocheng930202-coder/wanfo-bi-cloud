package com.wanfo.bi.security;

import java.util.*;
import com.wanfo.bi.domain.BiDatasetFieldDO;
import com.wanfo.bi.query.CalcFormulaCompiler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DerivedFieldPolicyTest {
    private BiDatasetFieldDO field(String name,String formula) {
        var f=new BiDatasetFieldDO();f.setOriginName(name);f.setExtFlag(formula==null?"0":"1");f.setExtFormula(formula);return f;
    }
    @Test void bareAndBracketedReferencesResolveWithoutTreatingLiteralsAsFields() {
        assertEquals(Set.of("amount"),CalcFormulaCompiler.references("COALESCE(AMOUNT, [amount]) + 1",Set.of("amount")));
        assertEquals(Set.of(),CalcFormulaCompiler.references("CONCAT('amount', 'text')",Set.of("amount")));
        assertTrue(CalcFormulaCompiler.compile("CONCAT('[amount]', 'it''s text')",Set.of("amount")).contains("'[amount]'"));
    }
    @Test void deniedAndMaskedDependenciesBlockTransitiveDerivedValues() {
        var fields=List.of(field("amount",null),field("name",null),field("gross","[amount]*2"),field("net","gross-1"),field("safe","UPPER(name)"));
        for(boolean mask:List.of(false,true)) {
            var result=DerivedFieldPolicy.expand(fields,new DatasetDataPolicy.Scope(List.of(),mask?Set.of():Set.of("amount"),mask?Set.of("amount"):Set.of()));
            assertTrue(result.denied().containsAll(Set.of("gross","net")));assertFalse(result.denied().contains("safe"));
            assertEquals(mask?Set.of("amount"):Set.of(),result.masked());
        }
    }
    @Test void statementsSubqueriesUnknownFunctionsAndFieldsAreRejected() {
        for(String expression:List.of("(SELECT amount FROM secret)","SLEEP(1)","missingField+1","t.amount+1","amount;DELETE FROM t"))
            assertThrows(IllegalArgumentException.class,()->CalcFormulaCompiler.compile(expression,Set.of("amount")),expression);
    }
    @Test void invalidStoredExpressionFailsClosed() {
        assertEquals(403,assertThrows(BiAccessException.class,()->DerivedFieldPolicy.expand(List.of(field("bad","missing+1")),DatasetDataPolicy.Scope.unrestricted())).getCode());
    }
}
