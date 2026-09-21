package com.sajo.market_service.support.exception;

import com.sajo.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SupportErrorCode implements ErrorCode {

    INVALID_QUESTION(
            HttpStatus.BAD_REQUEST,
            "SUPPORT_0001",
            "질문 내용은 비어 있을 수 없습니다"
    ),

    NO_RELEVANT_DOCUMENT_FOUND(
            HttpStatus.NOT_FOUND,
            "SUPPORT_0002",
            "질문과 관련된 문서를 찾지 못했습니다"
    ),

    LLM_RESPONSE_FAILED(
            HttpStatus.BAD_GATEWAY,
            "SUPPORT_0003",
            "챗봇 응답 생성에 실패했습니다"
    );

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
