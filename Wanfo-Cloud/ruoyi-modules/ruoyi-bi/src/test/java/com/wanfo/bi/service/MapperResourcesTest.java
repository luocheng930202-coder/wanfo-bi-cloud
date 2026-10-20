package com.wanfo.bi.service;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Parse the production XML and annotation mappings, including MyBatis DTD ordering. */
class MapperResourcesTest {
    @Test void productionMappersLoadAndBindStatements() throws Exception {
        var configuration=new Configuration();
        var resources=new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/bi/*.xml");
        assertTrue(resources.length>0);
        for(var resource:resources)try(var input=resource.getInputStream()) {
            new XMLMapperBuilder(input,configuration,resource.toString(),configuration.getSqlFragments()).parse();
        }
        assertNotNull(configuration.getMappedStatement("com.wanfo.bi.mapper.BiDataFormRecordMapper.listVisible"));
        assertNotNull(configuration.getMappedStatement("com.wanfo.bi.mapper.BiDataFormRecordMapper.finishSubmission"));
        for(String method:java.util.List.of("listVisible","listByFormId","findSubmission")){
            String sql=configuration.getMappedStatement("com.wanfo.bi.mapper.BiDataFormRecordMapper."+method).getBoundSql(java.util.Map.of("formId",1L,"owner",1L,"requestKey","test","admin",false)).getSql();
            assertTrue(sql.contains("review_note")&&sql.contains("review_time")&&sql.contains("review_by"),method+" must return persisted reconciliation metadata");
        }
    }
}
