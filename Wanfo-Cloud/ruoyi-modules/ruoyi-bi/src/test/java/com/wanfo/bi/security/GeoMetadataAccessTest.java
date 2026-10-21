package com.wanfo.bi.security;

import java.util.Set;
import com.wanfo.bi.controller.GeoController;
import com.wanfo.bi.service.GeoService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeoMetadataAccessTest {
    HandlerMethod metadata() throws Exception{return new HandlerMethod(new GeoController(new GeoService(new ObjectMapper())),"regions");}
    @Test void fixedPublicBoundariesRemainAvailableToVerifiedDashboardViewersWithoutChartManagement() throws Exception {
        var actors=mock(CurrentActorProvider.class);var capabilities=mock(CapabilityProvider.class);
        when(actors.currentActor()).thenReturn(new BiActor("test",1L,"viewer",null,Set.of()));
        assertTrue(new BiSecurityInterceptor(actors,capabilities).preHandle(new MockHttpServletRequest(),new MockHttpServletResponse(),metadata()));
        verify(actors).currentActor();verifyNoInteractions(capabilities);
    }
    @Test void boundaryMetadataDoesNotBypassSessionVerification() throws Exception {
        var actors=mock(CurrentActorProvider.class);var capabilities=mock(CapabilityProvider.class);
        when(actors.currentActor()).thenThrow(new BiAccessException(401,"login required"));
        assertThrows(BiAccessException.class,()->new BiSecurityInterceptor(actors,capabilities).preHandle(new MockHttpServletRequest(),new MockHttpServletResponse(),metadata()));
    }
}
