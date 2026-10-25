package com.wanfo.bi.query;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class UserQueryCacheTest {
    @Test void cacheCopiesRowsPreservesPrecisionAndStripsDiagnostics(){
        var cache=new UserQueryCache();var original=new LinkedHashMap<String,Object>();var amount=new BigDecimal("1234567890123456.123456789012");
        original.put("rows",new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("amount",amount)))));original.put("executedSql","secret SQL");original.put("params",List.of("private"));original.put("queryDiagnostics",Map.of("requestId","old"));
        cache.put("one",1L,300,cache.epoch(),original);var first=cache.get("one");assertNotNull(first);assertFalse(first.containsKey("executedSql"));assertFalse(first.containsKey("params"));assertFalse(first.containsKey("queryDiagnostics"));
        @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)first.get("rows");assertEquals(amount,rows.get(0).get("amount"));rows.get(0).put("amount",0);
        assertTrue(cache.get("one").toString().contains(amount.toString()));
    }
    @Test void identitiesPoliciesAndSnapshotVersionsProduceSeparateKeysAndClearRejectsLateWrites(){
        var cache=new UserQueryCache();assertNotEquals(cache.key(List.of("user-a","policy-a",1)),cache.key(List.of("user-b","policy-a",1)));assertNotEquals(cache.key(List.of("user-a","policy-a",1)),cache.key(List.of("user-a","policy-b",1)));assertNotEquals(cache.key(List.of("user-a",1)),cache.key(List.of("user-a",2)));
        long before=cache.epoch();cache.clear(1L);cache.put("late",1L,300,before,Map.of("rows",List.of()));assertNull(cache.get("late"));
    }
    @Test void cacheCapacityIsBoundedAndOversizedEntriesAreNotStored(){
        var cache=new UserQueryCache();for(int i=0;i<220;i++)cache.put("k"+i,1L,300,cache.epoch(),Map.of("rows",List.of(i)));
        assertEquals(200,cache.stats().get("entries"));assertNull(cache.get("k0"));
        cache.put("huge",1L,300,cache.epoch(),Map.of("rows",List.of("x".repeat(600000))));assertNull(cache.get("huge"));
    }
}
