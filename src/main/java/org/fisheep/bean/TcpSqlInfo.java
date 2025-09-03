package org.fisheep.bean;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * TCP SQL信息类
 * 用于存储TCP连接中的SQL信息
 */
@Data
public class TcpSqlInfo {

    /**
     * SQL字节数据（兼容字段）
     */
    private byte[] sql;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * SQL语句列表
     */
    private List<SqlStatement> sqlStatements = new ArrayList<>();

    /**
     * 添加SQL语句
     * @param sqlStatement SQL语句
     */
    public void addSqlStatement(SqlStatement sqlStatement) {
        this.sqlStatements.add(sqlStatement);
    }

    /**
     * 获取SQL语句列表
     * @return SQL语句列表
     */
    public List<SqlStatement> getSqlStatements() {
        return sqlStatements;
    }

    /**
     * 设置SQL语句列表
     * @param sqlStatements SQL语句列表
     */
    public void setSqlStatements(List<SqlStatement> sqlStatements) {
        this.sqlStatements = sqlStatements;
    }

}
