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

    /**
     * 주의: 이름과 달리 "질문이 문서 내용과 무관함"을 감지해서 던지는 예외가 아니다.
     * {@code SearchRequest}에 similarityThreshold를 두지 않아 유사도 점수와 무관하게 항상
     * topK개의 문서를 반환하므로, 벡터 저장소에 청크가 하나라도 있으면 이 예외는 절대 발생하지
     * 않는다. 실제로는 {@code SupportDocumentIngestionRunner}의 문서 적재가 실패해 벡터 저장소가
     * 비어 있는 경우에만 발생한다("질문과 무관한 질문"에 대한 응답은 LLM 시스템 프롬프트가
     * "문서에서 관련 내용을 찾지 못했습니다"로 자연어 처리한다). 실제 유사도 기반 무관 질문 감지가
     * 필요해지면 similarityThreshold를 도입하고 그때 이 예외의 트리거 조건을 다시 검토해야 한다.
     */
    NO_RELEVANT_DOCUMENT_FOUND(
            HttpStatus.NOT_FOUND,
            "SUPPORT_0002",
            "질문과 관련된 문서를 찾지 못했습니다"
    ),

    LLM_RESPONSE_FAILED(
            HttpStatus.BAD_GATEWAY,
            "SUPPORT_0003",
            "챗봇 응답 생성에 실패했습니다"
    ),

    /**
     * vectorStore.similaritySearch() 호출(임베딩 API 호출 포함)이 실패한 경우. LLM 호출 실패와
     * 마찬가지로 외부 AI 서비스 호출 실패이므로 동일하게 BAD_GATEWAY로 매핑해 GlobalExceptionHandler의
     * 일반 500(INTERNAL_SERVER_ERROR)으로 새어나가지 않도록 한다.
     */
    DOCUMENT_SEARCH_FAILED(
            HttpStatus.BAD_GATEWAY,
            "SUPPORT_0004",
            "관련 문서 검색에 실패했습니다"
    );

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
