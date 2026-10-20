package com.wanfo.bi.query;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalcFormulaCompilerTest
{
    @Test void fieldReferencesAreQuoted()
    {
        String sql = CalcFormulaCompiler.compile("[amount] * 2", Set.of("amount"));
        assertTrue(sql.contains("\"amount\""));
    }

    @Test void unknownFieldIsRejected()
    {
        assertThrows(IllegalArgumentException.class,
                () -> CalcFormulaCompiler.compile("[missing] + 1", Set.of("amount")));
    }

    @Test void injectionTokensAreRejected()
    {
        assertThrows(IllegalArgumentException.class,
                () -> CalcFormulaCompiler.compile("[amount]; DROP", Set.of("amount")));
    }
}
