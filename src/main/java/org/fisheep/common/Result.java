package org.fisheep.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Result {

    private int code = 200;

    private String message = "success";

    private Object result;



    public Result(Object result) {
        this.result = result;
    }

    public Result(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Result(ErrorEnum error){
        this.code = error.getCode();
        this.message = error.getMsg();
    }

}
