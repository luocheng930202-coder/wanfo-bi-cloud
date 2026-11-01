package com.wanfo.bi.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;
import org.junit.jupiter.api.Test;

class MysqlProtocolBatchPublisherTest {
    @Test
    void mysqlTypeMapsCommonJdbcTypes() {
        assertEquals("BIGINT NOT NULL",
                MysqlProtocolBatchPublisher.mysqlType(new MysqlProtocolBatchPublisher.Column("id", "BIGINT", Types.BIGINT, 0, 0, false)));
        assertEquals("VARCHAR(65533) NULL",
                MysqlProtocolBatchPublisher.mysqlType(new MysqlProtocolBatchPublisher.Column("name", "VARCHAR", Types.VARCHAR, 0, 0, true)));
        assertEquals("DECIMAL(18,2) NOT NULL",
                MysqlProtocolBatchPublisher.mysqlType(new MysqlProtocolBatchPublisher.Column("amt", "DECIMAL", Types.DECIMAL, 18, 2, false)));
        assertEquals("DATETIME NULL",
                MysqlProtocolBatchPublisher.mysqlType(new MysqlProtocolBatchPublisher.Column("ts", "TIMESTAMP", Types.TIMESTAMP, 0, 0, true)));
    }

    @Test
    void parseEngineRecognizesWarehouseTargets() {
        assertEquals(WarehousePublisher.Engine.CLICKHOUSE, WarehousePublisher.parseEngine("ck"));
        assertEquals(WarehousePublisher.Engine.DORIS, WarehousePublisher.parseEngine("Doris"));
        assertEquals(WarehousePublisher.Engine.STARROCKS, WarehousePublisher.parseEngine("starrocks"));
        assertThrows(IllegalArgumentException.class, () -> WarehousePublisher.parseEngine("mysql"));
    }

    @Test
    void mergeRequiresKeysSameAsClickHouseContract() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> WarehousePublisher.publishResultSet(null, WarehousePublisher.Engine.DORIS, null, "db", "t", 1, 100, 100,
                        WarehousePublisher.PublishMode.MERGE, null, null, null));
        assertTrue(error.getMessage().contains("MERGE"));
    }
}
