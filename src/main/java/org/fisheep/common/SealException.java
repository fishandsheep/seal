package org.fisheep.common;

import lombok.Getter;

/**
 * @author BigOrange
 */
@Getter
public class SealException extends Exception {

    private int code;
    private String msg;

    public SealException() {
        super();
    }

    public SealException(String message, int code) {
        super(message);
        this.msg = message;
        this.code = code;
    }

    public SealException(String message, int code, Throwable cause) {
        super(message, cause);
        this.msg = message;
        this.code = code;
    }

    public SealException(Throwable cause) {
        super(cause);
    }

    public SealException(ErrorEnum errorEnum) {
        super(errorEnum.getMsg());
        this.code = errorEnum.getCode();
        this.msg = errorEnum.getMsg();
    }
}
