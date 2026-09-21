package com.sajo.market_service.support.dto.response;

/**
 * 답변 생성에 실제로 참조된 문서 조각(청크) 하나를 가리킨다.
 *
 * @param documentTitle 문서 전체 제목 (예: "Market Service Overview")
 * @param sectionTitle  이 청크가 속한 섹션 제목 (예: "5. 지금까지 구현된 조회 API")
 * @param excerpt       근거로 삼은 원문 일부(전체 청크가 아니라 앞부분만 발췌 — 응답 크기 절약)
 */
public record SupportSourceReference(
        String documentTitle,
        String sectionTitle,
        String excerpt
) {
}
