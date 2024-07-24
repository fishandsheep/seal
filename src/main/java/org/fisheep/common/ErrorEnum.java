package org.fisheep.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorEnum {

    //数据库连接失败
    MySQL_CONNECTION(10001, "数据库连接失败");

    private Integer code;

    private String msg;

}
