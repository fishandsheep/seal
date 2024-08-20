package org.fisheep.bean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Status {

    //状态 0-进行中，1-完成，2-出错，3-被取消
    private int status;

    //总数
    private int total;

    //成功个数
    private int success;

    //错误个数
    private int error;

    //错误信息
    private String errorMessage;

    //百分比
    private double process;

    //完成时间戳
    private String timestamp;
}
