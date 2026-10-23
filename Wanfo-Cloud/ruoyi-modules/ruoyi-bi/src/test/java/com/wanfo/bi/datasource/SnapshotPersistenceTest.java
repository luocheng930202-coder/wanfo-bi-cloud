package com.wanfo.bi.datasource;
import com.wanfo.bi.mapper.BiSourceSnapshotMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotPersistenceTest {
    @Test void migrationAndMapperRoundtripSupportAtomicVersionedReplacement()throws Exception {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:snapshot_storage;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(var connection=ds.getConnection();var st=connection.createStatement()){
            st.execute("CREATE TABLE bi_datasource(id BIGINT PRIMARY KEY)");
            try(var input=getClass().getResourceAsStream("/db/migration/bi/V21__bi_source_snapshots.sql")){
                assertNotNull(input);for(String sql:new String(input.readAllBytes(),StandardCharsets.UTF_8).split(";"))if(!sql.isBlank())st.execute(sql);
            }
        }
        var configuration=new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));configuration.addMapper(BiSourceSnapshotMapper.class);
        var factory=new SqlSessionFactoryBuilder().build(configuration);
        try(var session=factory.openSession(true)){
            var mapper=session.getMapper(BiSourceSnapshotMapper.class);
            var row=new BiSourceSnapshotMapper.Row(7L,"api","encrypted-config","[]","[]",0,1,null,null);
            assertEquals(1,mapper.insert(row));assertEquals("encrypted-config",mapper.get(7L).configCipher());assertEquals(1,mapper.get(7L).version());
            var update=new BiSourceSnapshotMapper.Row(7L,"api","new-config","[]","[[\"北京\"]]",1,1,null,null);
            assertEquals(1,mapper.replace(update));assertEquals(2,mapper.get(7L).version());assertEquals(0,mapper.replace(update));assertEquals(1,mapper.get(7L).rowCount());
        }
    }
}
