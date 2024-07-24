package org.fisheep.bean;

import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.common.concurrent.ReadWriteLocked;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlStatements extends ReadWriteLocked {
    private final Map<String, List<SqlStatement>> sqlStatements = new HashMap<>();

    public void add(String id, List<SqlStatement> statementList) {
        this.add(id, statementList, StorageManagerFactory.getInstance());
    }

    private void add(String id, List<SqlStatement> statementList, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            this.sqlStatements.put(id, statementList);
            persistenceStoring.store(sqlStatements);
        });
    }

    public List<SqlStatement> all(String id) {
        return this.read(() ->
                this.sqlStatements.get(id)
        );
    }
}
