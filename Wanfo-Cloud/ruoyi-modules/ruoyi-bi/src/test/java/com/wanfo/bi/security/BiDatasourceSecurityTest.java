package com.wanfo.bi.security;

import java.util.Set;
import com.wanfo.bi.adapter.ruoyi.RuoYiIdentityAdapter;
import com.wanfo.bi.adapter.ruoyi.RuoYiBiWebAdvice;
import com.wanfo.bi.controller.BiDatasourceController;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.query.CalciteQueryEngine;
import com.wanfo.bi.service.BiDataSourceService;
import com.wanfo.common.security.service.TokenService;
import com.wanfo.system.api.domain.SysUser;
import com.wanfo.system.api.model.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BiDatasourceSecurityTest
{
    private TokenService tokens;
    private PrincipalDirectory principals;
    private BiDataSourceService service;
    private MockMvc mvc;
    private BiResourceAuthorizer resources;

    @BeforeEach
    void setup()
    {
        tokens = mock(TokenService.class);
        principals = mock(PrincipalDirectory.class);
        service = mock(BiDataSourceService.class);
        resources = mock(BiResourceAuthorizer.class);
        when(resources.allowed(anyString(), anyLong(), anyString())).thenReturn(true);
        when(service.get(anyLong())).thenReturn(new com.wanfo.bi.domain.BiDatasourceDO());
        when(principals.resolve("ruoyi", "user", "42", "alice")).thenReturn(1001L);
        RuoYiIdentityAdapter adapter = new RuoYiIdentityAdapter(tokens, principals);
        mvc = MockMvcBuilders.standaloneSetup(new BiDatasourceController(
                mock(DataSourceProvider.class), mock(CalciteQueryEngine.class), service, adapter, resources, mock(PrincipalCatalog.class)))
                .addInterceptors(new BiSecurityInterceptor(adapter, adapter))
                .setControllerAdvice(new RuoYiBiWebAdvice()).build();
    }

    private LoginUser session(String... permissions)
    {
        SysUser user = new SysUser();
        user.setUserId(42L);
        user.setUserName("alice");
        user.setStatus("0");
        user.setDelFlag("0");
        LoginUser login = new LoginUser();
        login.setSysUser(user);
        login.setExpireTime(System.currentTimeMillis() + 60000);
        login.setPermissions(Set.of(permissions));
        when(tokens.getLoginUser("valid")).thenReturn(login);
        return login;
    }

    @Test
    void anonymousAndForgedIdentityHeadersCannotRead()
            throws Exception
    {
        mvc.perform(get("/bi/datasource/manage").header("user_id", "1").header("username", "admin"))
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service, principals);
    }

    @Test
    void invalidAndExpiredTokensCannotRead() throws Exception
    {
        mvc.perform(get("/bi/datasource/manage").header("Authorization", "Bearer invalid"))
                .andExpect(jsonPath("$.code").value(401));
        session("*:*:*").setExpireTime(System.currentTimeMillis() - 1);
        mvc.perform(get("/bi/datasource/manage").header("Authorization", "Bearer valid"))
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service, principals);
    }

    @Test
    void readOnlyUserCannotWriteEvenViaDirectRequest() throws Exception
    {
        session("bi:datasource:list");
        mvc.perform(post("/bi/datasource").header("Authorization", "Bearer valid")
                .contentType("application/json").content("{\"name\":\"demo\"}"))
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(service);
    }

    @Test
    void authorizedReadReturnsRuoYiEnvelope() throws Exception
    {
        session("bi:datasource:list");
        when(service.listForManage()).thenReturn(java.util.List.of(new java.util.LinkedHashMap<>(java.util.Map.of("id", 8L, "name", "demo"))));
        mvc.perform(get("/bi/datasource/manage").header("Authorization", "Bearer valid"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("demo"));
        verify(principals).resolve("ruoyi", "user", "42", "alice");
    }

    @Test
    void writeUsesVerifiedUsernameInsteadOfSpoofedHeader() throws Exception
    {
        session("bi:datasource:edit");
        mvc.perform(put("/bi/datasource/8/status").header("Authorization", "Bearer valid")
                .header("username", "admin").contentType("application/json").content("{\"status\":\"1\"}"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).changeStatus(8L, "1", "alice");
    }

    @Test
    void wildcardGrantWorksButMissingAndOtherGrantsDoNot() throws Exception
    {
        session("bi:datasource:*");
        mvc.perform(delete("/bi/datasource/8").header("Authorization", "Bearer valid"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).delete(8L, "alice");
        session("system:user:list");
        mvc.perform(get("/bi/datasource/manage").header("Authorization", "Bearer valid"))
                .andExpect(jsonPath("$.code").value(403));
        session().setPermissions(null);
        mvc.perform(get("/bi/datasource/manage").header("Authorization", "Bearer valid"))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void everyDatasourceRouteRejectsUserWithoutItsCapability() throws Exception
    {
        session("system:user:list");
        for (var request : java.util.List.of(
                get("/bi/datasource/manage"), get("/bi/datasource/list"),
                post("/bi/datasource"), put("/bi/datasource"),
                put("/bi/datasource/8/status"), delete("/bi/datasource/8"),
                post("/bi/datasource/probe-test"), post("/bi/datasource/demo/test"),
                post("/bi/datasource/reload"), get("/bi/datasource/probe"),
                get("/bi/datasource/principals"), get("/bi/datasource/8/grants"),
                post("/bi/datasource/8/grants"), delete("/bi/datasource/8/grants/1/view")))
        {
            mvc.perform(request.header("Authorization", "Bearer valid"))
                    .andExpect(jsonPath("$.code").value(403));
        }
        verifyNoInteractions(service);
    }

    @Test
    void alternativeHostAdapterCanUseSameControllerAndInterceptor() throws Exception
    {
        CurrentActorProvider actors = () -> new BiActor("test-host", 500L, "external-user", null, Set.of());
        CapabilityProvider capabilities = permission -> {
            if (!"bi:datasource:edit".equals(permission))
            {
                throw new BiAccessException(403, "denied");
            }
        };
        MockMvc alternative = MockMvcBuilders.standaloneSetup(new BiDatasourceController(
                mock(DataSourceProvider.class), mock(CalciteQueryEngine.class), service, actors, resources, mock(PrincipalCatalog.class)))
                .addInterceptors(new BiSecurityInterceptor(actors, capabilities))
                .setControllerAdvice(new RuoYiBiWebAdvice()).build();
        alternative.perform(put("/bi/datasource/8/status").contentType("application/json")
                .content("{\"status\":\"1\"}"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).changeStatus(8L, "1", "external-user");
        alternative.perform(get("/bi/datasource/manage"))
                .andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(tokens, principals);
    }
}
