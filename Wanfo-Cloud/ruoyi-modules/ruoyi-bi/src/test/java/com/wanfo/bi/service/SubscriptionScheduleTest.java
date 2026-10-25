package com.wanfo.bi.service;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SubscriptionScheduleTest {
    @Test void dailyScheduleUsesLocalTimeAndUtcStorageAcrossDaylightSaving(){
        var schedule=new SubscriptionSchedule("daily",0,"09:30",List.of(),"America/New_York");
        Instant next=schedule.nextAfter(Instant.parse("2026-03-07T15:00:00Z"));
        assertEquals("2026-03-08T09:30-04:00[America/New_York]",next.atZone(ZoneId.of("America/New_York")).toString());
    }
    @Test void weeklyScheduleRequiresValidSelectedWeekdays(){
        assertThrows(IllegalArgumentException.class,()->new SubscriptionSchedule("weekly",0,"08:00",List.of(0),"UTC"));
        var schedule=new SubscriptionSchedule("weekly",0,"08:00",List.of(5,1),"UTC");
        assertEquals(Instant.parse("2026-10-30T08:00:00Z"),schedule.nextAfter(Instant.parse("2026-10-26T09:00:00Z")));
    }
}
