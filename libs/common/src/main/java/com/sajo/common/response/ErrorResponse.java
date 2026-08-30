package com.sajo.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sajo.common.code.ErrorCode;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(boolean success, String errorCode, String message, Map<String, String> errors) {

    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(false, errorCode.getErrorCode(), errorCode.getMessage(), null));
    }

    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode, Map<String, String> errors) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(false, errorCode.getErrorCode(), errorCode.getMessage(), errors));
    }

    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(false, errorCode.getErrorCode(), message, null));
    }

}
