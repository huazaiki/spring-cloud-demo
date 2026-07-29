package com.huazaiki.auth.exception;

import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.exception.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        return ResponseEntity
                .status(ex.getCode() >= 500 ? 500 : 400)
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }
}
