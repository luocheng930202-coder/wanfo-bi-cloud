package com.wanfo.bi.datasource.snapshot;

import org.apache.calcite.DataContext;
import org.apache.calcite.linq4j.*;
import org.apache.calcite.rel.type.*;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;

/** Each query reads one consistent snapshot, with the same cancellation boundary as JDBC. */
public final class SnapshotTable extends AbstractTable implements ScannableTable {
    private final SnapshotData data;
    public SnapshotTable(SnapshotData data){this.data=data;}
    @Override public RelDataType getRowType(RelDataTypeFactory factory){
        var builder=factory.builder();
        for(var c:data.columns()) {
            RelDataType type=switch(c.type()) {
                case "DECIMAL"->factory.createSqlType(SqlTypeName.DECIMAL,38,12);
                case "DATE"->factory.createSqlType(SqlTypeName.DATE);
                case "TIMESTAMP"->factory.createSqlType(SqlTypeName.TIMESTAMP);
                case "BOOLEAN"->factory.createSqlType(SqlTypeName.BOOLEAN);
                default->factory.createSqlType(SqlTypeName.VARCHAR,SnapshotData.MAX_CELL);
            };
            builder.add(c.name(),factory.createTypeWithNullability(type,true));
        }
        return builder.build();
    }
    @Override public Enumerable<Object[]> scan(DataContext context){
        var run=com.wanfo.bi.query.QueryGovernor.current();
        return new AbstractEnumerable<>() {
            public Enumerator<Object[]> enumerator(){return new Enumerator<>() {
                int index=-1;
                public Object[] current(){var row=data.rows().get(index);Object[] result=new Object[row.size()];for(int i=0;i<result.length;i++)result[i]=data.value(i,row.get(i));return result;}
                public boolean moveNext(){if(run!=null)try{run.check();}catch(com.wanfo.bi.query.QueryGovernanceException e){throw new RuntimeException(e);}var cancel=DataContext.Variable.CANCEL_FLAG.<java.util.concurrent.atomic.AtomicBoolean>get(context);if(cancel!=null && cancel.get())throw new IllegalStateException("查询已取消");return ++index<data.rows().size();}
                public void reset(){index=-1;}
                public void close(){}
            };}
        };
    }
}
