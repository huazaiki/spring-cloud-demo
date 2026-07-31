package com.huazaiki.common.exception;

public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode error) {
        super(error.getMessage());
        this.code = error.getCode();
    }

    public BusinessException(ErrorCode error, String message) {
        super(message);
        this.code = error.getCode();
    }

    public int getCode() {
        return code;
    }
}
