package org.fisheep.bean.data;

import org.fisheep.common.StorageManagerFactory;

public class Data {

    private final SqlStatements sqlStatements = new SqlStatements();

    private final Dbs dbs = new Dbs();

    public SqlStatements sqlStatements() {
        return this.sqlStatements;
    }

    public Dbs dbs() {
        return this.dbs;
    }

    public Data data() {
        return (Data) StorageManagerFactory.getInstance().root();
    }
}
