package com.sajo.common.feign;

import com.sajo.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
class FeignErrorCode implements ErrorCode {

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
