package org.fisheep.manager;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import io.javalin.http.Context;
import org.fisheep.bean.Db;
import org.fisheep.common.ErrorEnum;
import org.fisheep.common.Result;
import org.fisheep.common.SealException;
import org.fisheep.common.StorageManagerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author BigOrange
 */
public class DbManager {

    private static final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();

    public static void add(Context ctx) throws SealException {
        var db = ctx.bodyAsClass(Db.class);
        var dbs = StorageManagerFactory.data().dbs();
        var b = dbs.all().stream().anyMatch(db1 -> db1.getId().equals(db.getId()));
        if (b) {
            throw new SealException(ErrorEnum.MYSQL_CONNECTION_EXIST);
        }
        getDbVersion(db);
        dbs.add(db);
        var all = dbs.allNoPassword();
        ctx.json(new Result(all));
    }

    public static void delete(Context ctx) {
        var id = Integer.parseInt(ctx.pathParam("id"));
        var dbs = StorageManagerFactory.data().dbs();
        dbs.delete(id);
        var all = dbs.allNoPassword();
        ctx.json(new Result(all));
    }

    public static void all(Context ctx) {
        var all = StorageManagerFactory.data().dbs().allNoPassword();
        ctx.json(new Result(all));
    }

    public static void dbAndTimestamp(Context ctx) throws SealException {
        Map<String, List<String>> result = StorageManagerFactory.data().dbs().dbAndTimestamp();
        if (result.isEmpty()) {
            throw new SealException(ErrorEnum.TASK_NOT_EXIST);
        }
        ctx.json(new Result(result));
    }

    public static void getDbVersion(Db db) throws SealException {
        Connection connection;
        try {
            DataSource dataSource = createDataSource(db);
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

    public static DataSource createDataSource(Db db) {
        String id = db.getId();
        DataSource dataSource = dataSourceMap.get(id);
        if (dataSource != null) {
            return dataSource;
        }
        HikariConfig config = new HikariConfig();
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s", db.getUrl(), db.getPort(), db.getSchema());
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(db.getUsername());
        config.setPassword(db.getPassword());
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(3);
        config.setConnectionTimeout(6000);
        HikariDataSource hikariDataSource = new HikariDataSource(config);
        dataSourceMap.put(id, hikariDataSource);
        return hikariDataSource;
    }

}
