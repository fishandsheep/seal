package org.fisheep.bean.data;

import org.apache.commons.lang3.SerializationUtils;
import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.fisheep.bean.Db;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.common.concurrent.ReadWriteLocked;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
            List<Db> noPasswordDbs = this.dbs.stream().map(SerializationUtils::clone).collect(Collectors.toList());
            noPasswordDbs.forEach(db -> db.setPassword(null));
            return noPasswordDbs;
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

    private void add(Db db, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            db.setTimestamps(new ArrayList<>());
            this.dbs.add(db);
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
                String removeTimestampString = timestamps.remove(0);
                var data = StorageManagerFactory.data();
                data.sqlStatements().delete(dbs.get(id).getId() + removeTimestampString);
                data.status().delete(dbs.get(id).getId() + removeTimestampString);
            }
            timestamps.add(timestampString);
            dbs.get(id).setTimestamps(timestamps);
            persistenceStoring.store(this.dbs);
            return timestampString;
        });
    }
}
