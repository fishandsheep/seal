package org.fisheep.bean.data;

import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.eclipse.serializer.reference.Lazy;
import org.fisheep.bean.SqlStatement;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.common.concurrent.ReadWriteLocked;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author BigOrange
 */
public class SqlStatements extends ReadWriteLocked {

    private Lazy<Map<String, List<SqlStatement>>> lazyMap;

    public Map<String, List<SqlStatement>> getLazyMap() {
        return Lazy.get(this.lazyMap);
    }

    public void add(String id, List<SqlStatement> statementList) {
        this.add(id, statementList, StorageManagerFactory.getInstance());
    }

    public List<SqlStatement> one(String id) {
        return this.read(() -> Lazy.get(this.lazyMap).get(id));
    }

    public void delete(String id) {
        this.delete(id, StorageManagerFactory.getInstance());
    }

    private void add(String id, List<SqlStatement> statementList, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            Map<String, List<SqlStatement>> SqlStatementMap = this.getLazyMap();
            if (SqlStatementMap == null) {
                this.lazyMap = Lazy.Reference(SqlStatementMap = new HashMap<>());
            }
            SqlStatementMap.put(id, statementList);
            persistenceStoring.store(lazyMap);
        });
    }

    private void delete(String id, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            Map<String, List<SqlStatement>> lazyMap1 = getLazyMap();
            lazyMap1.remove(id);
            persistenceStoring.store(this.lazyMap);
        });
    }
}
