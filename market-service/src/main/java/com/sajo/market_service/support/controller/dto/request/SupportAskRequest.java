package com.sajo.market_service.support.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RAG 기반 고객 응대 챗봇에 대한 질문 요청.
 *
 * <p>question의 최대 길이를 제한한다. 제한이 없으면 지나치게 긴 질문이 그대로 임베딩 및
 * LLM 호출에 사용되어 비용·지연이 늘어날 수 있다. 500자는 실제 고객 문의 질문 길이를
 * 넉넉히 수용하면서도 남용을 막기 위한 값이다.</p>
 */
public record SupportAskRequest(
        @NotBlank(message = "질문 내용은 필수입니다.")
        @Size(max = 500, message = "질문은 500자를 초과할 수 없습니다.")
        String question
) {
}
