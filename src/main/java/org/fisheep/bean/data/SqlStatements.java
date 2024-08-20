package org.fisheep.bean.data;

import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.eclipse.serializer.reference.Lazy;
import org.fisheep.bean.SqlStatement;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.common.concurrent.ReadWriteLocked;

import java.util.*;

/**
 * @author BigOrange
 */
public class SqlStatements extends ReadWriteLocked {
    private final Map<String, Lazy<List<SqlStatement>>> sqlStatements = new HashMap<>();

    public void add(String id, List<SqlStatement> statementList) {
        this.add(id, statementList, StorageManagerFactory.getInstance());
    }

    public List<SqlStatement> one(String id) {
        return this.read(() ->
                Lazy.get(this.sqlStatements.get(id))
        );
    }

    public Map<String, List<String>> dbAndTimestamp() {
        return this.read(() -> {
            Map<String, List<String>> map = new HashMap<>();
            for (String key : sqlStatements.keySet()) {
                String[] split = key.split("\\|");
                String db = split[0];
                String timestamp = split[1];
                List<String> timestamps = map.computeIfAbsent(db, k -> new ArrayList<>());
                timestamps.add(timestamp);
            }
            map.replaceAll((dbKey, timestamps) -> {
                Collections.sort(timestamps);
                return timestamps;
            });
            return map;
        });
    }


    public void delete(String id){
        this.delete(id, StorageManagerFactory.getInstance());
    }

    private void add(String id, List<SqlStatement> statementList, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            this.sqlStatements.put(id, Lazy.Reference(statementList));
            persistenceStoring.store(sqlStatements);
        });
    }

    private void delete(String id, PersistenceStoring persistenceStoring) {
        this.write(() -> {
           this.sqlStatements.remove(id);
           persistenceStoring.store(this.sqlStatements);
        });
    }
}
