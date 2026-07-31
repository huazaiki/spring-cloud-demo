package com.huazaiki.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import com.huazaiki.common.exception.ErrorCode;

@DisplayName("ApiResponse")
class ApiResponseTest {

    @Nested
    @DisplayName("success factory methods")
    class Success {

        @Test
        @DisplayName("should create response with data and default 200 code")
        void shouldCreateSuccessWithData() {
            ApiResponse<String> response = ApiResponse.success("hello");
            assertEquals(200, response.getCode());
            assertEquals("success", response.getMessage());
            assertEquals("hello", response.getData());
        }

        @Test
        @DisplayName("should create empty success response")
        void shouldCreateSuccessWithoutData() {
            ApiResponse<Void> response = ApiResponse.success();
            assertEquals(200, response.getCode());
            assertEquals("success", response.getMessage());
            assertNull(response.getData());
        }
    }

    @Nested
    @DisplayName("fail factory methods")
    class Fail {

        @Test
        @DisplayName("should create fail response with explicit code and message")
        void shouldCreateFailWithCodeAndMessage() {
            ApiResponse<Void> response = ApiResponse.fail(400, "Bad Request");
            assertEquals(400, response.getCode());
            assertEquals("Bad Request", response.getMessage());
            assertNull(response.getData());
        }

        @Test
        @DisplayName("should create fail response from ErrorCode")
        void shouldCreateFailFromErrorCode() {
            ErrorCode error = () -> 404;
            ApiResponse<Void> response = ApiResponse.fail(error);
            assertEquals(404, response.getCode());
            assertEquals("Error", response.getMessage());
            assertNull(response.getData());
        }
    }
}
