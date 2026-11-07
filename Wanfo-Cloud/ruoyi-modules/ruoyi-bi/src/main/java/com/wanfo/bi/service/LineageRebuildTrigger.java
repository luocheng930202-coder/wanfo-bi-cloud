package com.wanfo.bi.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Schedules a derived lineage rebuild after the current change commits. */
@Component
public class LineageRebuildTrigger {
    private final ObjectProvider<LineageCollectionService> lineage;

    public LineageRebuildTrigger(ObjectProvider<LineageCollectionService> lineage) { this.lineage = lineage; }

    public void afterChange() {
        LineageCollectionService service = lineage.getIfAvailable();
        if (service == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { service.rebuildAfterChange(); }
            });
            return;
        }
        service.rebuildAfterChange();
    }
}
