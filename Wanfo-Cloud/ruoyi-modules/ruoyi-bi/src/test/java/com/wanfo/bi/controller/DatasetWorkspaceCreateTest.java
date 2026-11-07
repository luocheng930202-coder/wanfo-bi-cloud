package com.wanfo.bi.controller;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.wanfo.bi.domain.dataset.DatasetJoinModel;
import com.wanfo.bi.security.*;
import com.wanfo.bi.service.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatasetWorkspaceCreateTest {
    @Test void failedRelationshipRollsBackTheNewDatasetEvenForCheckedExceptions() throws Exception {
        Fixture f = fixture();
        doThrow(new SQLException("invalid relationship")).when(f.model()).applyJoin(eq(42L),any(),eq("tester"));
        assertThrows(SQLException.class,()->f.controller().createWorkspaceDataset(request()));
        assertEquals(0,f.jdbc().queryForObject("select count(*) from created_dataset",Integer.class));
    }
    @Test void successfulCreationAndRelationshipsCommitTogether() throws Exception {
        Fixture f = fixture();
        assertEquals(42L,f.controller().createWorkspaceDataset(request()).get("id"));
        assertEquals(1,f.jdbc().queryForObject("select count(*) from created_dataset",Integer.class));
        verify(f.model()).applyJoin(eq(42L),any(),eq("tester"));
    }
    private record Fixture(DatasetController controller,DatasetModelService model,JdbcTemplate jdbc){}
    private Fixture fixture() throws Exception {
        JdbcDataSource source=new JdbcDataSource();source.setURL("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc=new JdbcTemplate(source);jdbc.execute("create table created_dataset(id bigint primary key)");
        DatasetService service=mock(DatasetService.class);DatasetModelService model=mock(DatasetModelService.class);
        when(service.createTableDataset(any(),anyList(),eq("tester"))).thenAnswer(invocation->{jdbc.update("insert into created_dataset values (42)");return Map.of("id",42L);});
        CurrentActorProvider actors=()->new BiActor("test",1L,"tester",1L,Set.of());
        DatasetController controller=new DatasetController(service,model,mock(DatasetQualityRuleService.class),mock(QualityRuleVersionService.class),mock(QualityTaskService.class),actors,mock(BiResourceAuthorizer.class),mock(PrincipalCatalog.class),mock(DatasetDataPolicy.class));
        ProxyFactory proxy=new ProxyFactory(controller);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),new AnnotationTransactionAttributeSource()));
        return new Fixture((DatasetController)proxy.getProxy(),model,jdbc);
    }
    private DatasetController.WorkspaceCreateRequest request(){
        DatasetJoinModel model=new DatasetJoinModel();DatasetJoinModel.JoinStep join=new DatasetJoinModel.JoinStep();
        join.setJoinType("LEFT");join.setRightTable("school");join.setLeftKey("school_id");join.setRightKey("id");join.setRightFields(List.of("name"));model.setJoins(List.of(join));
        return new DatasetController.WorkspaceCreateRequest(Map.of("name","test","datasourceId",1,"sourceCatalog","demo","sourceTable","orders","fields",List.of("school_id")),model);
    }
}
