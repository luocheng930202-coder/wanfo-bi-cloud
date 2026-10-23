package com.wanfo.bi.query;

import java.util.Set;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.dialect.MysqlSqlDialect;

/** Calcite lowers TIMESTAMPADD to interval multiplication; MySQL needs it emitted as a function. */
public final class CalendarMysqlDialect extends MysqlSqlDialect {
    public CalendarMysqlDialect(SqlDialect.Context context) { super(context); }

    @Override public void unparseCall(SqlWriter writer, SqlCall call, int leftPrec, int rightPrec) {
        if(call.getKind()==SqlKind.IS_NOT_DISTINCT_FROM || call.getKind()==SqlKind.IS_DISTINCT_FROM){
            var equality=new SqlBinaryOperator("<=>",SqlKind.OTHER,30,true,null,null,null).createCall(call.getParserPosition(),call.operand(0),call.operand(1));
            if(call.getKind()==SqlKind.IS_DISTINCT_FROM)org.apache.calcite.sql.fun.SqlStdOperatorTable.NOT.createCall(call.getParserPosition(),equality).unparse(writer,leftPrec,rightPrec);
            else equality.getOperator().unparse(writer,equality,leftPrec,rightPrec);
            return;
        }
        if (call.getKind()==SqlKind.PLUS && call.operandCount()==2 && call.operand(1) instanceof SqlCall product
                && Set.of(SqlKind.TIMES,SqlKind.CHECKED_TIMES).contains(product.getKind()) && (product.operand(0) instanceof SqlIntervalLiteral || product.operand(1) instanceof SqlIntervalLiteral)) {
            unparseSqlDatetimeArithmetic(writer,call,SqlKind.PLUS,leftPrec,rightPrec);return;
        }
        super.unparseCall(writer,call,leftPrec,rightPrec);
    }

    @Override public void unparseSqlDatetimeArithmetic(SqlWriter writer, SqlCall call, SqlKind kind, int leftPrec, int rightPrec) {
        if (kind == SqlKind.PLUS && call.operand(1) instanceof SqlCall product && Set.of(SqlKind.TIMES,SqlKind.CHECKED_TIMES).contains(product.getKind())) {
            SqlNode interval = product.operand(0), amount = product.operand(1);
            if (!(interval instanceof SqlIntervalLiteral)) { interval=product.operand(1);amount=product.operand(0); }
            if (interval instanceof SqlIntervalLiteral literal) {
                var value=literal.getValueAs(SqlIntervalLiteral.IntervalValue.class);
                var qualifier=value.getIntervalQualifier();
                if (value.getSign()==1 && "1".equals(value.getIntervalLiteral()) && qualifier.getEndUnit()==null
                        && Set.of("DAY","MONTH").contains(qualifier.getStartUnit().name())) {
                    var frame=writer.startFunCall("TIMESTAMPADD");
                    writer.keyword(qualifier.getStartUnit().name());writer.sep(",",true);amount.unparse(writer,0,0);
                    writer.sep(",",true);call.operand(0).unparse(writer,0,0);writer.endFunCall(frame);return;
                }
            }
        }
        super.unparseSqlDatetimeArithmetic(writer,call,kind,leftPrec,rightPrec);
    }
}
