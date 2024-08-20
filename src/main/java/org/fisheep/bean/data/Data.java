package org.fisheep.bean.data;

/**
 * @author BigOrange
 */
public class Data {

    private final SqlStatements sqlStatements = new SqlStatements();

    private final Dbs dbs = new Dbs();

    private final Statuses statuses = new Statuses();

    public SqlStatements sqlStatements() {
        return this.sqlStatements;
    }

    public Dbs dbs() {
        return this.dbs;
    }

    public Statuses status() {
        return this.statuses;
    }

}
