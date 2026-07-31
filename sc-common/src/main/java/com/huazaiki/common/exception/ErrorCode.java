package com.huazaiki.common.exception;

public interface ErrorCode {

    int getCode();

    default String getMessage() {
        return "Error";
    }
}
