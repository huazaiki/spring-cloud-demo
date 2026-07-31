package com.huazaiki.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BusinessException")
class BusinessExceptionTest {

    @Test
    @DisplayName("should carry ErrorCode values")
    void shouldCarryErrorCode() {
        ErrorCode notFound = () -> 404;
        BusinessException ex = new BusinessException(notFound);

        assertEquals(404, ex.getCode());
        assertEquals("Error", ex.getMessage());
    }

    @Test
    @DisplayName("should accept override message")
    void shouldAcceptOverrideMessage() {
        ErrorCode badRequest = () -> 400;
        BusinessException ex = new BusinessException(badRequest, "Invalid supplier name");

        assertEquals(400, ex.getCode());
        assertEquals("Invalid supplier name", ex.getMessage());
    }
}
