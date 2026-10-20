package com.wanfo.bi.security;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataFormPolicyTest {
    final Map<String,String> types=Map.of("region","varchar","amount","decimal","secret","varchar");
    DatasetDataPolicy.Scope scope(String field,String op,String value) {
        return new DatasetDataPolicy.Scope(List.of(new DatasetDataPolicy.Rule(1L,1L,2L,field,op,value,"admin")),Set.of(),Set.of());
    }
    @Test void fixedRegionAndNumericBoundsConstrainWrites() {
        assertDoesNotThrow(()->DataFormPolicy.requireWrite(Map.of("region","east"),types,scope("region","EQ","east")));
        assertThrows(BiAccessException.class,()->DataFormPolicy.requireWrite(Map.of("region","west"),types,scope("region","EQ","east")));
        assertThrows(BiAccessException.class,()->DataFormPolicy.requireWrite(Map.of(),types,scope("region","EQ","east")));
        assertTrue(DataFormPolicy.matches(Map.of("amount",10),types,scope("amount","GE","9")));
        assertFalse(DataFormPolicy.matches(Map.of("amount",2),types,scope("amount","GE","9")));
    }
    @Test void hiddenAndMaskedFieldsCannotBeWritten() {
        for(var scope:List.of(new DatasetDataPolicy.Scope(List.of(),Set.of("secret"),Set.of()),new DatasetDataPolicy.Scope(List.of(),Set.of(),Set.of("secret"))))
            assertThrows(BiAccessException.class,()->DataFormPolicy.requireWrite(Map.of("secret","raw"),types,scope));
    }
    @Test void recordsDropHiddenUnknownAndMaskRestrictedValues() {
        var visible=DataFormPolicy.visible(Map.of("region","east","amount",99,"secret","raw","removed","old"),types,new DatasetDataPolicy.Scope(List.of(),Set.of("secret"),Set.of("amount")));
        assertEquals(Map.of("region","east","amount","***"),visible);
    }
    @Test void invalidRulesFailClosed() {
        assertThrows(BiAccessException.class,()->DataFormPolicy.matches(Map.of(),types,scope("removed","EQ","x")));
        assertThrows(BiAccessException.class,()->DataFormPolicy.matches(Map.of("amount",1),types,scope("amount","GE","bad")));
    }
}
