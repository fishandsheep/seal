package org.fisheep.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author BigOrange
 */

@Getter
@AllArgsConstructor
public enum ErrorEnum {

    //数据库连接失败
    MySQL_CONNECTION_FAIL(10001, "the database connection failed"),

    MYSQL_NO_VERSION(10002, "failed to get the database version"),

    MYSQL_LOW_VERSION(10003, "the database version cannot be earlier than 8.0"),

    MYSQL_CONNECTION_EXIST(10004, "the database connection already exists"),

    FILE_READ_FAIL(20001, "failed to read the file"),

    FILE_IS_EMPTY(20002, "the file is empty"),

    TASK_NOT_FOUND(30001,"this task is not queried, please select it again"),

    TASK_NOT_EXIST(30002,"task does not exist, please create it");

    private final Integer code;

    private final String msg;

}
