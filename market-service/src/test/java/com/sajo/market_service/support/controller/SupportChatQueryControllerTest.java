package com.sajo.market_service.support.controller;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.market_service.support.controller.dto.response.SupportAskResponse;
import com.sajo.market_service.support.controller.dto.response.SupportSourceReference;
import com.sajo.market_service.support.exception.SupportErrorCode;
import com.sajo.market_service.support.service.query.SupportChatQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code SupportChatQueryController}는 다른 도메인의 Query 컨트롤러들과 동일하게
 * {@code @WebMvcTest} + {@code GlobalExceptionHandler}로 요청 검증 실패 응답과 정상 응답
 * 매핑을 컨트롤러 레벨에서 검증한다(코드 리뷰로 이 슬라이스 테스트가 없다는 점이 지적됐다).
 *
 * <p>컨트롤러가 {@code @ConditionalOnProperty(sajo.support.rag.enabled=true)}로 게이팅되어
 * 있어, {@code @TestPropertySource}로 이 플래그를 명시적으로 켜야 {@code @WebMvcTest}가
 * 컨트롤러 빈을 로드할 수 있다.</p>
 */
@WebMvcTest(SupportChatQueryController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "sajo.support.rag.enabled=true")
class SupportChatQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupportChatQueryService supportChatQueryService;

    @Test
    @DisplayName("정상 질문에는 답변과 출처를 담아 200으로 응답한다")
    void returnsAnswerWithSourcesForValidQuestion() throws Exception {
        UUID userId = UUID.randomUUID();
        SupportAskResponse response = new SupportAskResponse(
                "현재가 조회는 GET /api/v1/market/quote로 조회할 수 있습니다.",
                List.of(new SupportSourceReference(
                        "Market Service Overview",
                        "5. 지금까지 구현된 조회 API > 현재가 조회",
                        "GET /api/v1/market/quote?stockCode=005930"))
        );
        given(supportChatQueryService.ask(userId, "현재가는 어떻게 조회하나요?")).willReturn(response);

        mockMvc.perform(post("/api/v1/support/ask")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"현재가는 어떻게 조회하나요?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.answer").value(response.answer()))
                .andExpect(jsonPath("$.data.sources[0].documentTitle").value("Market Service Overview"))
                .andExpect(jsonPath("$.data.sources[0].sectionTitle")
                        .value("5. 지금까지 구현된 조회 API > 현재가 조회"));

        verify(supportChatQueryService).ask(userId, "현재가는 어떻게 조회하나요?");
    }

    @Test
    @DisplayName("question이 비어 있으면 400과 검증 에러 스키마를 반환한다")
    void returnsBadRequestWhenQuestionIsBlank() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/support/ask")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.question").exists());
    }

    @Test
    @DisplayName("question이 500자를 초과하면 400과 검증 에러 스키마를 반환한다")
    void returnsBadRequestWhenQuestionExceedsMaxLength() throws Exception {
        UUID userId = UUID.randomUUID();
        String tooLongQuestion = "가".repeat(501);

        mockMvc.perform(post("/api/v1/support/ask")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + tooLongQuestion + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.question").exists());
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 400을 반환한다")
    void returnsBadRequestWhenUserIdHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/support/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"현재가는 어떻게 조회하나요?\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("서비스가 문서 검색 실패 예외를 던지면 502로 응답한다")
    void returnsBadGatewayWhenDocumentSearchFails() throws Exception {
        UUID userId = UUID.randomUUID();
        given(supportChatQueryService.ask(userId, "현재가는 어떻게 조회하나요?"))
                .willThrow(new BusinessException(SupportErrorCode.DOCUMENT_SEARCH_FAILED));

        mockMvc.perform(post("/api/v1/support/ask")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"현재가는 어떻게 조회하나요?\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SUPPORT_0004"));
    }
}
