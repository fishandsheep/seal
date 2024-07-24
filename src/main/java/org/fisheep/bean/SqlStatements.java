package org.fisheep.bean;

import org.eclipse.serializer.persistence.types.PersistenceStoring;
import org.fisheep.common.Result;
import org.fisheep.common.StorageManagerFactory;
import org.fisheep.common.concurrent.ReadWriteLocked;

import java.util.List;

public class SqlStatements extends ReadWriteLocked {
    private List<SqlStatement> sqlStatements;

    public void add(List<SqlStatement> statementList) {
        this.add(statementList, StorageManagerFactory.getInstance());
    }

    private void add(List<SqlStatement> statementList, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            this.sqlStatements = statementList;
            persistenceStoring.store(sqlStatements);
        });
    }

    public List<SqlStatement> all() {
        this.read(() -> {

        });
    }
}
