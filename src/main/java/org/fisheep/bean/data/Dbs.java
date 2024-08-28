package org.fisheep.bean.data;

import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.fisheep.bean.Db;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.common.concurrent.ReadWriteLocked;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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


    public Db one(int id) {
        return this.read(() -> this.dbs.get(id));
    }

    public List<Db> all() {
        return this.read(() -> this.dbs);
    }

    public List<Db> allNoPassword() {
        return this.read(() -> {
            List<Db> dbList = new ArrayList<>();
            this.dbs.forEach(db -> {
                Db builder = Db.builder().url(db.getUrl())
                        .port(db.getPort())
                        .schema(db.getSchema())
                        .username(db.getUsername())
                        .version(db.getVersion()).build();
                dbList.add(builder);
            });
            return dbList;
        });
    }

    public String addTimestamp(int id) {
        return this.addTimestamp(id, StorageManagerFactory.getInstance());
    }

    public Map<String, List<String>> dbAndTimestamp() {
        return this.read(() -> {
            Map<String, List<String>> map = new HashMap<>();
            dbs.forEach(db -> {
                map.put(db.getId(), db.getTimestamps());
            });
            return map;
        });
    }

    private void delete(int id, PersistenceStoring persistenceStoring) {
        this.dbs.remove(id);
        persistenceStoring.store(this.dbs);
    }

    private void add(Db db, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            ArrayList<String> timestamps = new ArrayList<>();
            db.setTimestamps(timestamps);
            this.dbs.add(db);
            persistenceStoring.store(this.dbs);
        });
    }

    private String addTimestamp(int id, PersistenceStoring persistenceStoring) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss");
        String timestampString = now.format(formatter);
        return this.write(() -> {
            var db = dbs.get(id);
            List<String> timestamps = new ArrayList<>(db.getTimestamps());
            if (timestamps.size() == 5) {
                String removeTimestampString = timestamps.remove(0);
                var data = StorageManagerFactory.data();
                data.sqlStatements().delete(db.getId() + removeTimestampString);
                data.status().delete(db.getId() + removeTimestampString);
            }
            timestamps.add(timestampString);
            dbs.get(id).setTimestamps(timestamps);
            persistenceStoring.store(this.dbs.get(id));
            return timestampString;
        });
    }
}
