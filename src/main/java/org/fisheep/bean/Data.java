package org.fisheep.bean;

import org.fisheep.common.StorageManagerFactory;

public class Data {

    private static Data data = null;
    private final SqlStatements sqlStatements = new SqlStatements();

    private final Dbs dbs = new Dbs();

//    public static Data newInstance() {
//        if (data == null){
//            return Data.newInstance().data();
//        }
//        return data;
//    }

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
