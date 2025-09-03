package org.fisheep.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 应用配置类
 * 统一管理应用配置参数
 */
@Data
@Slf4j
public class AppConfig {
    
    private static AppConfig instance;
    
    // 数据库连接池配置
    private final DatabaseConfig databaseConfig;
    
    // SOAR工具配置
    private final String soarPath;
    
    // 应用配置
    private final AppConfig.ApplicationConfig appConfig;
    
    private AppConfig() {
        this.databaseConfig = new DatabaseConfig();
        this.soarPath = getRequiredProperty("soar.path");
        this.appConfig = new ApplicationConfig();
    }
    
    public static AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }
    
    private String getRequiredProperty(String key) {
        String value = System.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Required property '" + key + "' is not set");
        }
        return value;
    }
    
    private static String getProperty(String key, String defaultValue) {
        String value = System.getProperty(key);
        return value != null ? value : defaultValue;
    }
    
    @Data
    public static class DatabaseConfig {
        private final int maxPoolSize;
        private final int connectionTimeout;
        private final int maxTimestampSize = 5;
        private final String minMysqlVersion = "8.0";
        
        public DatabaseConfig() {
            this.maxPoolSize = Integer.parseInt(AppConfig.getProperty("db.max-pool-size", "3"));
            this.connectionTimeout = Integer.parseInt(AppConfig.getProperty("db.connection-timeout", "6000"));
        }
    }
    
    @Data
    public static class ApplicationConfig {
        private final int rateLimitPerMinute = 10;
        private final int rateLimitPerSecond = 1;
        private final long lazyCacheDurationMinutes = 1;
        private final double lazyCacheThreshold = 0.75;
        private final int serverPort = 7070;
        private final String contextPath = "/seal";
    }
}