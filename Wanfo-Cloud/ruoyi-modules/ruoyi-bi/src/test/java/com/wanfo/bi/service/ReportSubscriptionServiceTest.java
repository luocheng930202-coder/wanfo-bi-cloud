package com.wanfo.bi.service;

import com.wanfo.bi.mapper.BiReportSubscriptionMapper;
import com.wanfo.bi.security.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportSubscriptionServiceTest {
    @SuppressWarnings("unchecked")
    private ReportSubscriptionService service(CapabilityProvider caps,boolean enabled){
        ObjectProvider<JavaMailSender> mail=mock(ObjectProvider.class);when(mail.getIfAvailable()).thenReturn(null);
        return new ReportSubscriptionService(mock(BiReportSubscriptionMapper.class),mock(ExportJobService.class),
            ()->new BiActor("test",1L,"alice",null,Set.of()),caps,new ObjectMapper(),mail,enabled,"example.com",""," ");
    }
    @Test void subscriptionsAreDisabledUntilEnabled(){
        var service=service(mock(CapabilityProvider.class),false);
        var request=new ReportSubscriptionService.Create("chart",7L,null,"xlsx","日报",java.util.List.of("reader@example.com"),1440);
        assertTrue(assertThrows(IllegalArgumentException.class,()->service.create(request)).getMessage().contains("尚未启用"));
    }
    @Test void createRequiresSubscriptionPermission(){
        CapabilityProvider caps=mock(CapabilityProvider.class);doThrow(new BiAccessException(403,"denied")).when(caps).require("bi:subscription:add");
        var service=service(caps,true);
        assertThrows(BiAccessException.class,()->service.create(null));
    }
}
