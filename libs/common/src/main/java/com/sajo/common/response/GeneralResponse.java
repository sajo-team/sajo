package com.sajo.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sajo.common.code.ResponseCode;
import org.springframework.http.ResponseEntity;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeneralResponse<T>(boolean success, String message, T data) {

    public static <T> ResponseEntity<GeneralResponse<T>> toResponseEntity(ResponseCode responseCode, T data) {
        return ResponseEntity.status(responseCode.getStatus())
                .body(new GeneralResponse<>(true, responseCode.getMessage(), data));
    }
}
