package org.fisheep.bean.data;

/**
 * 数据根类
 * 管理所有数据存储对象
 */
public class Data {

    private final SqlStatements sqlStatements = new SqlStatements();

    private final Dbs dbs = new Dbs();

    private final Statuses statuses = new Statuses();

    private final ExplainResults explainResults = new ExplainResults();

    public SqlStatements sqlStatements() {
        return this.sqlStatements;
    }

    public Dbs dbs() {
        return this.dbs;
    }

    public Statuses statuses() {
        return this.statuses;
    }

    public ExplainResults explainResults() {
        return this.explainResults;
    }
}
