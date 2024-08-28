package org.fisheep.bean.data;

import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.fisheep.bean.Status;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.common.concurrent.ReadWriteLocked;

import java.util.HashMap;

public class Statuses extends ReadWriteLocked {

    private final HashMap<String, Status> statuses = new HashMap<>();

    public void put(String explainId, Status status) {
        this.put(explainId, status, StorageManagerFactory.getInstance());
    }

    public void delete(String explainId) {
        this.delete(explainId, StorageManagerFactory.getInstance());
    }

    public Status one(String explainId) {
        return this.read(() -> this.statuses.get(explainId));
    }

    private void put(String explainId, Status status, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            this.statuses.put(explainId, status);
            persistenceStoring.store(this.statuses);
        });
    }

    private void delete(String explainId, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            this.statuses.remove(explainId);
            persistenceStoring.store(this.statuses);
        });
    }
}
