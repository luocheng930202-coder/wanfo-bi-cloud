package com.wanfo.bi.security;

import com.wanfo.bi.adapter.ruoyi.RuoYiBiWebAdvice;
import com.wanfo.bi.adapter.ruoyi.RuoYiIdentityAdapter;
import com.wanfo.bi.controller.CatalogAssetController;
import com.wanfo.bi.datasource.BiDatasource;
import com.wanfo.bi.datasource.DataSourceProvider;
import com.wanfo.bi.mapper.BiMetadataScanMapper;
import com.wanfo.bi.mapper.BiResourceMapper;
import com.wanfo.bi.service.CatalogAssetService;
import com.wanfo.bi.service.CatalogAuditService;
import com.wanfo.common.security.service.TokenService;
import com.wanfo.system.api.domain.SysUser;
import com.wanfo.system.api.model.LoginUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class CatalogAssetHttpSecurityTest
{
    private TokenService tokens;
    private PrincipalDirectory principals;
    private BiResourceMapper resourceMapper;
    private BiMetadataScanMapper metadataMapper;
    private MockMvc mvc;

    @BeforeEach
    void setup()
    {
        tokens = mock(TokenService.class);
        principals = mock(PrincipalDirectory.class);
        resourceMapper = mock(BiResourceMapper.class);
        metadataMapper = mock(BiMetadataScanMapper.class);
        DataSourceProvider sources = mock(DataSourceProvider.class);
        when(sources.listEnabled()).thenReturn(List.of(source(1L), source(2L)));
        when(principals.resolve("ruoyi", "user", "42", "alice")).thenReturn(1001L);
        when(principals.resolve("ruoyi", "user", "43", "alice")).thenReturn(1002L);
        when(resourceMapper.owner("datasource", 1L)).thenReturn(1001L);
        when(resourceMapper.owner("datasource", 2L)).thenReturn(2002L);
        when(resourceMapper.granted(anyString(), anyLong(), anyString())).thenReturn(List.of());

        RuoYiIdentityAdapter identity = new RuoYiIdentityAdapter(tokens, principals);
        BiResourceAuthorizer resources = new BiResourceAuthorizer(resourceMapper, identity, identity);
        CatalogAssetService service = new CatalogAssetService(metadataMapper, sources, resources,
                identity, new ObjectMapper(), identity);
        mvc = MockMvcBuilders.standaloneSetup(new CatalogAssetController(service, mock(CatalogAuditService.class)))
                .addInterceptors(new BiSecurityInterceptor(identity, identity))
                .setControllerAdvice(new RuoYiBiWebAdvice()).build();
    }

    @Test
    void anonymousRequestCannotSearchTheCatalog() throws Exception
    {
        mvc.perform(get("/bi/catalog/assets"))
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(metadataMapper, resourceMapper);
    }

    @Test
    void scopedUserSearchesOnlyOwnedDatasourceAndCannotProbeHiddenAssets() throws Exception
    {
        login("scoped", "bi:datasource:model");
        when(metadataMapper.searchAssets(List.of(1L), null, null, "orders", 0, 20))
                .thenReturn(List.of(Map.of("id", 901L, "sourceId", 1L, "name", "orders")));
        when(metadataMapper.countAssets(List.of(1L), null, null, "orders")).thenReturn(1L);
        when(metadataMapper.assetById(902L)).thenReturn(Map.of("id", 902L, "sourceKind", "CONNECTOR",
                "sourceId", 2L, "assetType", "TABLE"));
        when(metadataMapper.visibleAsset(902L, List.of(1L))).thenReturn(null);

        mvc.perform(get("/bi/catalog/assets").header("Authorization", "Bearer scoped").param("query", "orders"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].sourceId").value(1));

        mvc.perform(get("/bi/catalog/assets").header("Authorization", "Bearer scoped").param("datasourceId", "2"))
                .andExpect(jsonPath("$.code").value(403));

        mvc.perform(get("/bi/catalog/assets/902").header("Authorization", "Bearer scoped"))
                .andExpect(jsonPath("$.code").value(404));

        mvc.perform(get("/bi/catalog/assets/902/versions").header("Authorization", "Bearer scoped"))
                .andExpect(jsonPath("$.code").value(404));

        verify(metadataMapper).searchAssets(List.of(1L), null, null, "orders", 0, 20);
        verify(metadataMapper).countAssets(List.of(1L), null, null, "orders");
        verify(metadataMapper, never()).assetVersions(902L);
        verify(metadataMapper, never()).searchAssets(eq(List.of(2L)), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void scopedUserWithoutDatasourceGrantsGetsEmptyUnfilteredScope() throws Exception
    {
        login("scoped-empty", "bi:datasource:model");

        mvc.perform(get("/bi/catalog/assets").header("Authorization", "Bearer scoped-empty"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());

        verifyNoInteractions(metadataMapper);
    }

    private void login(String token, String... permissions)
    {
        SysUser user = new SysUser();
        user.setUserId("scoped-empty".equals(token) ? 43L : 42L);
        user.setUserName("alice");
        user.setStatus("0");
        user.setDelFlag("0");
        LoginUser login = new LoginUser();
        login.setSysUser(user);
        login.setExpireTime(System.currentTimeMillis() + 60_000);
        login.setPermissions(Set.of(permissions));
        when(tokens.getLoginUser(token)).thenReturn(login);
    }

    private static BiDatasource source(Long id)
    {
        BiDatasource source = new BiDatasource();
        source.setId(id);
        source.setStatus("0");
        source.setSchemaName("source_" + id);
        return source;
    }
}
