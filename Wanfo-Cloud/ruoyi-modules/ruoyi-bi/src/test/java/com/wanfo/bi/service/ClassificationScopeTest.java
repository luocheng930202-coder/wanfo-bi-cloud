package com.wanfo.bi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wanfo.bi.mapper.ClassificationMapper;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.DatasetDataPolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClassificationScopeTest {

    @Test
    void connectorPreviewMasksAndDeniesClassifiedFields() {
        ClassificationMapper mapper = mock(ClassificationMapper.class);
        CapabilityProvider caps = mock(CapabilityProvider.class);
        when(caps.has("bi:resource:admin")).thenReturn(false);
        when(mapper.connectorColumnLevels(eq(9L), eq("orders"))).thenReturn(List.of(
                Map.of("fieldName", "phone", "levelCode", "CONFIDENTIAL", "enforcement", "MASK", "rankNo", 30),
                Map.of("fieldName", "id_card", "levelCode", "SECRET", "enforcement", "DENY", "rankNo", 40),
                Map.of("fieldName", "amount", "levelCode", "PUBLIC", "enforcement", "NONE", "rankNo", 10)
        ));
        ClassificationService service = new ClassificationService(mapper, caps, mock(CurrentActorProvider.class),
                mock(CatalogAuditService.class));
        DatasetDataPolicy.Scope scope = service.scopeForConnectorPreview(9L, "orders",
                List.of("phone", "id_card", "amount"));
        assertTrue(scope.masked().contains("phone"));
        assertTrue(scope.denied().contains("id_card"));
        assertEquals(0, scope.masked().stream().filter(f -> f.equals("id_card")).count());
    }

    @Test
    void adminBypassForConnectorPreview() {
        ClassificationMapper mapper = mock(ClassificationMapper.class);
        CapabilityProvider caps = mock(CapabilityProvider.class);
        when(caps.has("bi:resource:admin")).thenReturn(true);
        ClassificationService service = new ClassificationService(mapper, caps, mock(CurrentActorProvider.class),
                mock(CatalogAuditService.class));
        DatasetDataPolicy.Scope scope = service.scopeForConnectorPreview(9L, "orders", List.of("phone"));
        assertTrue(scope.masked().isEmpty());
        assertTrue(scope.denied().isEmpty());
    }
}
