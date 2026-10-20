package com.wanfo.bi.security;
import java.util.*;
import com.wanfo.bi.mapper.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DataPolicyTest {
    private DatasetDataPolicy.Rule rule(long principal,String field,String kind,String value) {
        return new DatasetDataPolicy.Rule(1L,9L,principal,field,kind,value,"admin");
    }
    @Test void multiplePrincipalsIntersectAndDenyWins() {
        var mapper=mock(BiDatasetPolicyMapper.class);var caps=mock(CapabilityProvider.class);
        var service=new DatasetDataPolicy(mapper,()->new BiActor("test",1L,"u",2L,Set.of(3L,4L)),caps,mock(BiDatasetFieldMapper.class),mock(BiDatasetTableMapper.class),mock(BiResourceMapper.class));
        when(mapper.list(9L)).thenReturn(List.of(rule(1,"city","EQ","A"),rule(2,"id","GT","0"),rule(3,"secret","MASK",null),rule(4,"secret","DENY",null),rule(99,"city","EQ","B")));
        var s=service.resolve(9L);assertEquals(2,s.rows().size());assertEquals(Set.of("secret"),s.denied());assertTrue(s.masked().isEmpty());
        when(mapper.list(9L)).thenReturn(List.of());assertTrue(service.resolve(9L).rows().isEmpty()); // no cached policy
    }
    @Test void administratorBypassDoesNotReadRules() {
        var mapper=mock(BiDatasetPolicyMapper.class);var caps=mock(CapabilityProvider.class);when(caps.has("bi:resource:admin")).thenReturn(true);
        var service=new DatasetDataPolicy(mapper,()->{throw new AssertionError();},caps,null,null,null);
        assertEquals(DatasetDataPolicy.Scope.unrestricted(),service.resolve(1L));verifyNoInteractions(mapper);
    }
    @Test void injectionValueIsBoundNotInterpolated() {
        String value="' OR 1=1 --";
        var scope=new DatasetDataPolicy.Scope(List.of(rule(1,"city","EQ",value)),Set.of(),Set.of());
        var q=DataPolicyCompiler.compile(List.of("city"),Map.of("city","VARCHAR"),"`",scope);
        assertEquals("`city` = ?",q.predicate());assertEquals(List.of(value),q.params());assertFalse(q.predicate().contains(value));
    }
    @Test void maskIsProjectedWithoutFetchingOriginalAndDenyIsOmitted() {
        var q=DataPolicyCompiler.compile(List.of("id","secret","phone"),Map.of("id","INT","secret","VARCHAR","phone","VARCHAR"),"`",new DatasetDataPolicy.Scope(List.of(),Set.of("secret"),Set.of("phone")));
        assertEquals("`id`, '***' AS `phone`",q.projection());assertEquals(List.of("id","phone"),q.columns());
    }
    @Test void allColumnsDeniedFailsClosed() {
        assertThrows(BiAccessException.class,()->DataPolicyCompiler.compile(List.of("id"),Map.of("id","INT"),"`",new DatasetDataPolicy.Scope(List.of(),Set.of("id"),Set.of())));
    }
    @Test void removedPolicyFieldFailsClosed() {
        assertThrows(BiAccessException.class,()->DataPolicyCompiler.compile(List.of("id"),Map.of("id","INT"),"`",new DatasetDataPolicy.Scope(List.of(rule(1,"gone","EQ","x")),Set.of(),Set.of())));
    }
    @Test void numericValuesValidatedAndTyped() {
        var types=Map.of("id","BIGINT");
        assertThrows(BiAccessException.class,()->DataPolicyCompiler.compile(List.of("id"),types,"`",new DatasetDataPolicy.Scope(List.of(rule(1,"id","EQ","1 OR 1=1")),Set.of(),Set.of())));
        var q=DataPolicyCompiler.compile(List.of("id"),types,"`",new DatasetDataPolicy.Scope(List.of(rule(1,"id","GE","12")),Set.of(),Set.of()));
        assertEquals(new java.math.BigDecimal("12"),q.params().get(0));
    }
    @Test void nullPredicatesAndIdentifierEscaping() {
        var q=DataPolicyCompiler.compile(List.of("a`b"),Map.of("a`b","VARCHAR"),"`",new DatasetDataPolicy.Scope(List.of(rule(1,"a`b","IS_NULL",null)),Set.of(),Set.of()));
        assertEquals("`a``b` IS NULL",q.predicate());assertTrue(q.params().isEmpty());
    }
    @Test void policyMutationRequiresAdministratorBeforeDatabaseRead() {
        var caps=mock(CapabilityProvider.class);doThrow(new BiAccessException(403,"denied")).when(caps).require("bi:resource:admin");var mapper=mock(BiDatasetPolicyMapper.class);
        var service=new DatasetDataPolicy(mapper,null,caps,null,null,null);
        assertThrows(BiAccessException.class,()->service.add(9L,rule(1,"id","DENY",null)));verifyNoInteractions(mapper);
    }
}
