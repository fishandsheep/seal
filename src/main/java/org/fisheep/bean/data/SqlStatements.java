package org.fisheep.bean.data;

import org.eclipse.serializer.collections.lazy.LazyArrayList;
import org.eclipse.serializer.collections.lazy.LazyList;
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
    private static final Map<String, Lazy<LazyList<SqlStatement>>> sqlStatements = new HashMap<>();

    public void add(String id, List<SqlStatement> statementList) {
        this.add(id, statementList, StorageManagerFactory.getInstance());
    }

    public LazyList<SqlStatement> one(String id) {
        return this.read(() ->
                Lazy.get(this.sqlStatements.get(id))
        );
    }

    public void delete(String id){
        this.delete(id, StorageManagerFactory.getInstance());
    }

    private void add(String id, List<SqlStatement> statementList, PersistenceStoring persistenceStoring) {
        this.write(() -> {
            LazyArrayList<SqlStatement> lazyArrayList = new LazyArrayList<>();
            lazyArrayList.addAll(statementList);
            this.sqlStatements.put(id, Lazy.Reference(lazyArrayList));
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
