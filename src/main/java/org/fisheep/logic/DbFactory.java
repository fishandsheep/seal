package org.fisheep.logic;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import org.fisheep.bean.Db;
import org.fisheep.common.ErrorEnum;
import org.fisheep.common.SealException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

public class DbFactory {

    public static volatile ConcurrentHashMap<String, DataSource> dataSources = new ConcurrentHashMap<>();

    public static volatile ConcurrentHashMap<String, Db> dbConfigs = new ConcurrentHashMap<>();

    public static void getDbVersion(Db db) throws SealException {
        Connection connection;
        try {
            DataSource dataSource = getDataSource(db);
            connection = dataSource.getConnection();
        } catch (HikariPool.PoolInitializationException | SQLException e) {
            throw new SealException(ErrorEnum.MySQL_CONNECTION_FAIL);
        }
        try {
            PreparedStatement statement = connection.prepareStatement("select version() as version");
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                db.setVersion(resultSet.getString("version"));
            }
            if (db.getVersion().compareTo("8.0") < 0) {
                throw new SealException(ErrorEnum.MYSQL_LOW_VERSION);
            }
        } catch (SQLException e) {
            throw new SealException(ErrorEnum.MYSQL_NO_VERSION);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
//                    throw new RuntimeException(e);
                }
            }
        }


    }

    public static DataSource getDataSource(Db db) {
        String id = db.getId();
        DataSource dataSource = dataSources.get(id);
        if (dataSource == null) {
            dataSource = createDataSource(db);
            dataSources.put(id, dataSource);
            dbConfigs.put(id, db);
        }
        return dataSource;
    }

    public static DataSource createDataSource(Db db) {
        HikariConfig config = new HikariConfig();
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s",
                db.getUrl(), db.getPort(), db.getSchema());
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(db.getUsername());
        config.setPassword(db.getPassword());
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(3);
        config.setConnectionTimeout(6000);
        return new HikariDataSource(config);
    }

}
