package org.fisheep.manager;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import lombok.extern.slf4j.Slf4j;
import org.fisheep.bean.Db;
import org.fisheep.common.ErrorEnum;
import org.fisheep.common.Result;
import org.fisheep.common.SealException;
import org.fisheep.config.AppConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库连接管理器
 * 负责数据库连接的创建、管理和版本检查
 */
@Slf4j
public class DatabaseManager extends AbstractManager<Db> {
    
    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final AppConfig.DatabaseConfig dbConfig;
    
    public DatabaseManager() {
        this.dbConfig = AppConfig.getInstance().getDatabaseConfig();
    }
    
    /**
     * 添加数据库连接
     */
    @Override
    protected Db doAdd(Db db) {
        getDbVersion(db);
        getStorage().dbs().add(db);
        return db;
    }
    
    /**
     * 删除数据库连接
     */
    @Override
    protected boolean doDelete(int id) {
        // 先关闭连接池
        Db db = doGetById(id);
        if (db != null) {
            closeDataSource(db.getId());
        }
        getStorage().dbs().delete(id);
        return true;
    }
    
    /**
     * 获取所有数据库连接（不包含密码）
     */
    @Override
    protected List<Db> doGetAll() {
        return getStorage().dbs().allNoPassword();
    }
    
    /**
     * 根据ID获取数据库连接
     */
    @Override
    protected Db doGetById(int id) {
        return getStorage().dbs().allNoPassword().stream()
                .filter(db -> db.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 更新数据库连接
     */
    @Override
    protected Db doUpdate(Db db) {
        // 先关闭旧连接池
        closeDataSource(db.getId());
        // 重新验证版本
        getDbVersion(db);
        // 更新存储
        getStorage().dbs().delete(Integer.parseInt(db.getId()));
        getStorage().dbs().add(db);
        return db;
    }
    
    /**
     * 验证数据库连接配置
     */
    @Override
    protected void validateEntity(Db db) throws SealException {
        if (db == null) {
            throw new SealException(ErrorEnum.INVALID_PARAMETER, "Database configuration cannot be null");
        }
        if (db.getId() == null || db.getId().trim().isEmpty()) {
            throw new SealException(ErrorEnum.INVALID_PARAMETER, "Database ID cannot be empty");
        }
        if (db.getUrl() == null || db.getUrl().trim().isEmpty()) {
            throw new SealException(ErrorEnum.INVALID_PARAMETER, "Database URL cannot be empty");
        }
        if (db.getUsername() == null || db.getUsername().trim().isEmpty()) {
            throw new SealException(ErrorEnum.INVALID_PARAMETER, "Database username cannot be empty");
        }
        if (db.getPassword() == null || db.getPassword().trim().isEmpty()) {
            throw new SealException(ErrorEnum.INVALID_PARAMETER, "Database password cannot be empty");
        }
    }
    
    /**
     * 检查数据库连接是否已存在
     */
    @Override
    protected boolean exists(Db db) {
        return getStorage().dbs().all().stream()
                .anyMatch(existingDb -> existingDb.getId().equals(db.getId()));
    }
    
    /**
     * 获取数据库版本信息
     */
    public void getDbVersion(Db db) throws SealException {
        Connection connection = null;
        try {
            DataSource dataSource = createDataSource(db);
            connection = dataSource.getConnection();
            
            try (PreparedStatement statement = connection.prepareStatement("SELECT version() as version");
                 ResultSet resultSet = statement.executeQuery()) {
                
                if (resultSet.next()) {
                    String version = resultSet.getString("version");
                    db.setVersion(version);
                    
                    if (version.compareTo(dbConfig.getMinMysqlVersion()) < 0) {
                        throw new SealException(ErrorEnum.MYSQL_LOW_VERSION);
                    }
                }
            }
        } catch (HikariPool.PoolInitializationException | SQLException e) {
            throw new SealException(ErrorEnum.MYSQL_CONNECTION_FAIL);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    log.warn("Failed to close database connection", e);
                }
            }
        }
    }
    
    /**
     * 创建数据源
     */
    public DataSource createDataSource(Db db) {
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
        config.setMaximumPoolSize(dbConfig.getMaxPoolSize());
        config.setConnectionTimeout(dbConfig.getConnectionTimeout());
        
        HikariDataSource hikariDataSource = new HikariDataSource(config);
        dataSourceMap.put(id, hikariDataSource);
        
        log.info("Created datasource for database: {}", id);
        return hikariDataSource;
    }
    
    /**
     * 关闭数据源
     */
    private void closeDataSource(String id) {
        DataSource dataSource = dataSourceMap.remove(id);
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
            log.info("Closed datasource for database: {}", id);
        }
    }
    
    /**
     * 获取数据库和时间戳信息
     */
    public Map<String, List<String>> getDbAndTimestamp() {
        Map<String, List<String>> result = getStorage().statuses().dbAndTimestamp();
        if (result.isEmpty()) {
            throw new SealException(ErrorEnum.TASK_NOT_EXIST);
        }
        return result;
    }
    
    /**
     * 获取所有数据库连接（用于API响应）
     */
    public Result getAllDatabaseConnections() {
        List<Db> all = doGetAll();
        return new Result(all);
    }
    
    /**
     * 添加数据库连接（用于API响应）
     */
    public Result addDatabaseConnection(Db db) {
        Db addedDb = add(db);
        return new Result(doGetAll());
    }
    
    /**
     * 删除数据库连接（用于API响应）
     */
    public Result deleteDatabaseConnection(int id) {
        boolean deleted = delete(id);
        if (deleted) {
            return new Result(doGetAll());
        }
        throw new SealException(ErrorEnum.ENTITY_NOT_FOUND);
    }
}