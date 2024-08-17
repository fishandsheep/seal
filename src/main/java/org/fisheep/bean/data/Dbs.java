package org.fisheep.bean.data;

import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.fisheep.bean.Db;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.common.concurrent.ReadWriteLocked;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

//    public void update(int id, Db db) {
//        this.update(id, db, StorageManagerFactory.getInstance());
//    }

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

    public String addTimestamp(int id) {
        return this.addTimestamp(id, StorageManagerFactory.getInstance());
    }

    private void add(Db db, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            db.setTimestamps(new ArrayList<>());
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

    private String addTimestamp(int id, PersistenceStoring persistenceStoring) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss");
        String timestampString = now.format(formatter);
        return this.write(() -> {
            List<String> timestamps = dbs.get(id).getTimestamps();
            if (timestamps.size() == 5) {
                timestamps.remove(0);
            }
            timestamps.add(timestampString);
            dbs.get(id).setTimestamps(timestamps);
            persistenceStoring.store(this.dbs);
            return timestampString;
        });
    }
}
