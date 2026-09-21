package com.sajo.market_service.support.controller.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * RAG 기반 고객 응대 챗봇에 대한 질문 요청.
 */
public record SupportAskRequest(
        @NotBlank(message = "질문 내용은 필수입니다.")
        String question
) {
}
