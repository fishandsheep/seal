package org.fisheep.bean.data;

import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.fisheep.bean.Db;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.common.concurrent.ReadWriteLocked;

import java.util.ArrayList;
import java.util.List;

/**
 * @author BigOrange
 */
public class Dbs extends ReadWriteLocked {

    private final List<Db> dbs = new ArrayList<>();

    public void add(Db db) {
        this.add(db, StorageManagerFactory.getInstance());
    }

    public void delete(int id) {
        this.delete(id, StorageManagerFactory.getInstance());
    }

    public void update(int id, Db db) {
        this.update(id, db, StorageManagerFactory.getInstance());
    }

    public Db one(int id) {
        return this.read(() ->
                this.dbs.get(id)
        );
    }

    public List<Db> all() {
        return this.read(() ->
                this.dbs
        );
    }

    private void add(Db db, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            this.dbs.add(db);
            persistenceStoring.store(this.dbs);
        });
    }

    private void update(int id, Db db, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            this.dbs.remove(id);
            this.dbs.add(id, db);
            persistenceStoring.store(this.dbs);
        });
    }

    private void delete(int id, PersistenceStoring persistenceStoring) {
        this.dbs.remove(id);
        persistenceStoring.store(this.dbs);
    }

}
