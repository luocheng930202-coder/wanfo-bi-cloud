package com.wanfo.bi.service;
import com.wanfo.bi.mapper.BiResourceHistoryMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class ResourceHistoryPersistenceTest {
    @Test void historyMigrationKeepsImmutableVersionsAndPrunesOnlyOldVersions()throws Exception {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:resource_history;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(var connection=ds.getConnection();var statement=connection.createStatement();var input=getClass().getResourceAsStream("/db/migration/bi/V22__bi_resource_history.sql")){
            assertNotNull(input);for(String sql:new String(input.readAllBytes(),StandardCharsets.UTF_8).split(";"))if(!sql.isBlank())statement.execute(sql);
        }
        var configuration=new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));configuration.addMapper(BiResourceHistoryMapper.class);
        try(var session=new SqlSessionFactoryBuilder().build(configuration).openSession(true)){
            var mapper=session.getMapper(BiResourceHistoryMapper.class);var store=new ResourceHistoryStore(mapper);
            for(int i=0;i<=55;i++)store.record("chart",7L,i,"save","{\"name\":\"v"+i+"\"}","author");
            assertEquals(50,mapper.list("chart",7L).size());assertNull(mapper.get("chart",7L,5));assertNotNull(mapper.get("chart",7L,6));
            store.record("chart",7L,55,"baseline","{\"name\":\"overwrite\"}","other");assertTrue(mapper.get("chart",7L,55).definitionJson().contains("v55"));assertNull(mapper.list("chart",7L).get(0).definitionJson());
            assertNull(mapper.get("dashboard",7L,55));assertTrue(Long.parseLong(mapper.get("chart",7L,55).createdAt().trim())>1700000000000L);
        }
    }
}
