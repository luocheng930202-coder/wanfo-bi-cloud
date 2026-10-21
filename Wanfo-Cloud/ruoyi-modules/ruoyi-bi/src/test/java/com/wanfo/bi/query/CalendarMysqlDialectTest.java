package com.wanfo.bi.query;

import org.apache.calcite.avatica.util.TimeUnit;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.dialect.MysqlSqlDialect;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalendarMysqlDialectTest {
    private final CalendarMysqlDialect dialect=new CalendarMysqlDialect(MysqlSqlDialect.DEFAULT_CONTEXT);
    private SqlCall expression(SqlKind multiplication,String unit,String count) throws Exception {
        var pos=SqlParserPos.ZERO;
        var interval=SqlLiteral.createInterval(1,count,new SqlIntervalQualifier(TimeUnit.valueOf(unit),null,pos),pos);
        var factor=SqlParser.create("-MOD(3, 7)").parseExpression();
        var product=new SqlBinaryOperator("*",multiplication,60,true,null,null,null).createCall(pos,interval,factor);
        return SqlStdOperatorTable.PLUS.createCall(pos,SqlParser.create("DATE '2026-01-04'").parseExpression(),product);
    }
    @Test void checkedAndUncheckedCalendarOffsetsEmitAValidThreeArgumentFunction() throws Exception {
        for(var kind:new SqlKind[]{SqlKind.TIMES,SqlKind.CHECKED_TIMES})for(String unit:new String[]{"DAY","MONTH"}) {
            String sql=expression(kind,unit,"1").toSqlString(dialect).getSql();
            assertTrue(sql.contains("TIMESTAMPADD("+unit+","),sql);
            assertFalse(sql.contains("INTERVAL"),sql);
            assertTrue(sql.contains("MOD(3, 7)"),sql);
        }
    }
    @Test void otherIntervalsAndOrdinaryMysqlCallsUseTheExistingDialect() throws Exception {
        assertFalse(expression(SqlKind.TIMES,"DAY","2").toSqlString(dialect).getSql().contains("TIMESTAMPADD"));
        var plain=SqlParser.create("SELECT SUM(3), CAST(DATE '2026-01-04' AS DATE)").parseQuery();
        assertEquals(plain.toSqlString(MysqlSqlDialect.DEFAULT).getSql(),plain.toSqlString(dialect).getSql());
    }
}
