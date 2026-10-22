package com.wanfo.bi.query;
import static org.junit.jupiter.api.Assertions.*;
import com.wanfo.bi.config.BiProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
class SlowQueryLogTest {
 @Test void slowRecordsCorrelateWithoutSensitiveContext() throws Exception {
  var properties=new BiProperties();properties.getQueryLimits().setSlowMillis(1);var governor=new QueryGovernor(properties,null);var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(QueryGovernor.class);var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();logger.addAppender(appender);
  try{var run=governor.admit("sensitive-user",List.of(1L));run.failure(new java.sql.SQLException("sensitive SQL parameter password"));Thread.sleep(5);run.close();long elapsed=run.elapsedMillis();Thread.sleep(5);assertEquals(elapsed,run.elapsedMillis());assertEquals(1,appender.list.size());var message=appender.list.get(0).getFormattedMessage();assertTrue(message.contains(run.id()));assertTrue(message.contains("source=background"));assertFalse(message.contains("sensitive"));assertFalse(message.contains("password"));assertEquals(run.id(),run.diagnostics().get("requestId"));assertEquals(true,run.diagnostics().get("slow"));}finally{logger.detachAppender(appender);appender.stop();}
 }
 @Test void fastQueryDoesNotCreateSlowRecord() throws Exception {
  var properties=new BiProperties();properties.getQueryLimits().setSlowMillis(60000);var governor=new QueryGovernor(properties,null);try(var run=governor.admit("user",List.of(1L))){assertEquals(false,run.diagnostics().get("slow"));}
 }
}
