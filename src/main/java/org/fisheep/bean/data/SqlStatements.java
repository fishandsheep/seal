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
    private final Map<String, Lazy<List<SqlStatement>>> sqlStatements = new HashMap<>();

    public void add(String id, List<SqlStatement> statementList) {
        this.add(id, statementList, StorageManagerFactory.getInstance());
    }

    private void add(String id, List<SqlStatement> statementList, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            this.sqlStatements.put(id, Lazy.Reference(statementList));
            persistenceStoring.store(sqlStatements);
        });
    }

    public List<SqlStatement> all(String id) {
        return this.read(() ->
                Lazy.get(this.sqlStatements.get(id))
        );
    }
}
