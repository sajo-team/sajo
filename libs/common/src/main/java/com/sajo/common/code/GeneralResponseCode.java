package com.sajo.common.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GeneralResponseCode implements ResponseCode {
    OK(HttpStatus.OK, "요청이 성공했습니다."),
    CREATED(HttpStatus.CREATED, "성공적으로 생성되었습니다"),
    ACCEPTED(HttpStatus.ACCEPTED, "요청이 접수되어 비동기로 처리됩니다");

    private final HttpStatus status;
    private final String message;
}
