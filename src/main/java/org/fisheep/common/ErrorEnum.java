package org.fisheep.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 * 统一管理系统错误码和错误信息
 */
@Getter
@AllArgsConstructor
public enum ErrorEnum {
    
    // 通用错误码 (10000-19999)
    INVALID_PARAMETER(10000, "Invalid parameter"),
    INTERNAL_ERROR(10001, "Internal server error"),
    ENTITY_NOT_FOUND(10002, "Entity not found"),
    ENTITY_ALREADY_EXISTS(10003, "Entity already exists"),
    
    // 数据库相关错误码 (11000-11999)
    MYSQL_CONNECTION_FAIL(11001, "Database connection failed"),
    MYSQL_NO_VERSION(11002, "Failed to get database version"),
    MYSQL_LOW_VERSION(11003, "Database version cannot be earlier than 8.0"),
    MYSQL_CONNECTION_EXIST(11004, "Database connection already exists"),
    MYSQL_INVALID_URL(11005, "Invalid database URL"),
    MYSQL_INVALID_CREDENTIALS(11006, "Invalid database credentials"),
    
    // 文件相关错误码 (12000-12999)
    FILE_READ_FAIL(12001, "Failed to read file"),
    FILE_IS_EMPTY(12002, "File is empty"),
    FILE_NOT_FOUND(12003, "File not found"),
    FILE_UPLOAD_FAILED(12004, "File upload failed"),
    FILE_FORMAT_NOT_SUPPORTED(12005, "File format not supported"),
    
    // 任务相关错误码 (13000-13999)
    TASK_NOT_FOUND(13001, "Task not found, please select again"),
    TASK_NOT_EXIST(13002, "Task does not exist, please create it"),
    TASK_ALREADY_RUNNING(13003, "Task is already running"),
    TASK_TIMEOUT(13004, "Task execution timeout"),
    
    // 业务相关错误码 (14000-14999)
    ANALYSIS_FAILED(14001, "SQL analysis failed"),
    INVALID_PCAP_FILE(14002, "Invalid PCAP file format"),
    RATE_LIMIT_EXCEEDED(14003, "Rate limit exceeded"),
    CONFIGURATION_ERROR(14004, "Configuration error");
    
    private final Integer code;
    private final String msg;
    
    /**
     * 根据错误码获取错误信息
     * @param code 错误码
     * @return 错误信息
     */
    public static String getMsgByCode(Integer code) {
        for (ErrorEnum error : values()) {
            if (error.getCode().equals(code)) {
                return error.getMsg();
            }
        }
        return "Unknown error";
    }
}
