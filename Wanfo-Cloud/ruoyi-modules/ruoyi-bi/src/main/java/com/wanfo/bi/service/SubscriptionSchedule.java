package com.wanfo.bi.service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Calendar schedule resolved in the user's IANA time zone and persisted as UTC. */
public record SubscriptionSchedule(String kind,int intervalMinutes,String localTime,List<Integer> weekdays,String timezone) {
    private static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT);
    public SubscriptionSchedule {
        kind=kind==null?"interval":kind.trim().toLowerCase(Locale.ROOT);
        timezone=timezone==null?"UTC":timezone.trim();
        try{ZoneId.of(timezone);}catch(Exception e){throw new IllegalArgumentException("请选择有效的时区");}
        if("interval".equals(kind)){if(intervalMinutes<15||intervalMinutes>10080)throw new IllegalArgumentException("执行间隔需为 15～10080 分钟");weekdays=List.of();localTime="";}
        else if("daily".equals(kind)||"weekly".equals(kind)){
            if(localTime==null)throw new IllegalArgumentException("请填写当地执行时间");
            try{LocalTime.parse(localTime,CLOCK);}catch(DateTimeParseException e){throw new IllegalArgumentException("执行时间须为 HH:mm");}
            if("weekly".equals(kind)){if(weekdays==null||weekdays.isEmpty()||weekdays.size()>7||weekdays.stream().anyMatch(d->d==null||d<1||d>7)||new HashSet<>(weekdays).size()!=weekdays.size())throw new IllegalArgumentException("请选择有效的星期（周一=1，周日=7）");weekdays=weekdays.stream().sorted().toList();}
            else weekdays=List.of();
        }else throw new IllegalArgumentException("不支持的订阅日历类型");
    }
    public Instant nextAfter(Instant after){
        ZoneId zone=ZoneId.of(timezone);
        if("interval".equals(kind))return after.plusSeconds(intervalMinutes*60L);
        ZonedDateTime local=after.atZone(zone);LocalTime time=LocalTime.parse(localTime,CLOCK);
        for(int offset=0;offset<=7;offset++){
            LocalDate date=local.toLocalDate().plusDays(offset);
            if("weekly".equals(kind)&&!weekdays.contains(date.getDayOfWeek().getValue()))continue;
            Instant candidate=ZonedDateTime.of(date,time,zone).toInstant();if(candidate.isAfter(after))return candidate;
        }
        throw new IllegalStateException("无法计算下次订阅时间");
    }
    public static LocalDateTime utc(Instant instant){return LocalDateTime.ofInstant(instant,ZoneOffset.UTC);}
}
