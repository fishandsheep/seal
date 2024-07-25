package org.fisheep.bean;

import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.common.concurrent.ReadWriteLocked;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Dbs extends ReadWriteLocked {

    private Map<String, Db> dbs = new HashMap<>();

    public void add(String id, Db db) {
        this.add(id, db, StorageManagerFactory.getInstance());
    }

    private void add(String id, Db db, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            this.dbs.put(id, db);
            persistenceStoring.store(dbs);
        });
    }

    public Db all(String id) {
        return this.read(() ->
                this.dbs.get(id)
        );
    }

}
