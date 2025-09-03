package org.fisheep.common;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 自定义异常类
 * 统一处理业务异常
 */
@Getter
@Slf4j
public class SealException extends RuntimeException {

    private final int code;
    private final String msg;

    /**
     * 默认构造函数
     */
    public SealException() {
        this(ErrorEnum.INTERNAL_ERROR);
    }

    /**
     * 使用错误枚举构造
     * @param errorEnum 错误枚举
     */
    public SealException(ErrorEnum errorEnum) {
        super(errorEnum.getMsg());
        this.code = errorEnum.getCode();
        this.msg = errorEnum.getMsg();
    }

    /**
     * 使用错误枚举和自定义消息构造
     * @param errorEnum 错误枚举
     * @param customMessage 自定义消息
     */
    public SealException(ErrorEnum errorEnum, String customMessage) {
        super(customMessage);
        this.code = errorEnum.getCode();
        this.msg = customMessage;
    }

    /**
     * 使用错误枚举和原因构造
     * @param errorEnum 错误枚举
     * @param cause 原因
     */
    public SealException(ErrorEnum errorEnum, Throwable cause) {
        super(errorEnum.getMsg(), cause);
        this.code = errorEnum.getCode();
        this.msg = errorEnum.getMsg();
    }

    /**
     * 使用错误枚举、自定义消息和原因构造
     * @param errorEnum 错误枚举
     * @param customMessage 自定义消息
     * @param cause 原因
     */
    public SealException(ErrorEnum errorEnum, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.code = errorEnum.getCode();
        this.msg = customMessage;
    }

    /**
     * 使用消息和错误码构造（保持向后兼容）
     * @param message 错误消息
     * @param code 错误码
     * @deprecated 使用 {@link #SealException(ErrorEnum, String)} 代替
     */
    @Deprecated
    public SealException(String message, int code) {
        super(message);
        this.code = code;
        this.msg = message;
    }

    /**
     * 使用消息、错误码和原因构造（保持向后兼容）
     * @param message 错误消息
     * @param code 错误码
     * @param cause 原因
     * @deprecated 使用 {@link #SealException(ErrorEnum, String, Throwable)} 代替
     */
    @Deprecated
    public SealException(String message, int code, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.msg = message;
    }

    /**
     * 使用原因构造（保持向后兼容）
     * @param cause 原因
     * @deprecated 使用 {@link #SealException(ErrorEnum, Throwable)} 代替
     */
    @Deprecated
    public SealException(Throwable cause) {
        super(cause);
        this.code = ErrorEnum.INTERNAL_ERROR.getCode();
        this.msg = ErrorEnum.INTERNAL_ERROR.getMsg();
    }

    /**
     * 获取错误详情
     * @return 错误详情字符串
     */
    public String getErrorDetails() {
        return String.format("Error[%d]: %s", code, msg);
    }

    /**
     * 记录异常日志
     */
    public void logError() {
        log.error("SealException occurred: {}", getErrorDetails(), this);
    }
}
