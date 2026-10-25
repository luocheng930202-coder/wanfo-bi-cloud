package com.wanfo.bi.domain.dataset;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DatasetJoinModelTest {
    @Test void oldSingleJoinPayloadMigratesToOneOrderedStep() {
        var model = new DatasetJoinModel();
        model.setJoinType("LEFT"); model.setRightTable("orders"); model.setLeftKey("id");
        model.setRightKey("customer_id"); model.setRightFields(List.of("amount"));
        var normalized = model.normalizedJoins();
        assertEquals(1, normalized.size());
        assertEquals("orders", normalized.get(0).getRightTable());
        assertEquals("r_", DatasetJoinModel.fieldPrefix(1));
    }

    @Test void multiJoinStepsKeepOrderAndUniqueGeneratedNamespaces() {
        var model = new DatasetJoinModel();
        var first = new DatasetJoinModel.JoinStep(); first.setRightTable("orders");
        var second = new DatasetJoinModel.JoinStep(); second.setRightTable("regions");
        model.setJoins(List.of(first, second));
        assertEquals(List.of("orders", "regions"), model.normalizedJoins().stream().map(DatasetJoinModel.JoinStep::getRightTable).toList());
        assertEquals("r2_", DatasetJoinModel.fieldPrefix(2));
        assertEquals("r8_", DatasetJoinModel.fieldPrefix(8));
        assertThrows(IllegalArgumentException.class, () -> DatasetJoinModel.fieldPrefix(9));
    }
}
