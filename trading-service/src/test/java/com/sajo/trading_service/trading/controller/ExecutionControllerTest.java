package com.sajo.trading_service.trading.controller;

import com.sajo.common.config.CommonPageableAutoConfiguration;
import com.sajo.common.exception.BusinessException;
import com.sajo.common.exception.GlobalExceptionHandler;
import com.sajo.trading_service.trading.controller.dto.response.ExecutionResponse;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.service.query.ExecutionQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutionController.class)
@Import({
        GlobalExceptionHandler.class,
        CommonPageableAutoConfiguration.class
})
class ExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExecutionQueryService executionQueryService;

    @Test
    @DisplayName("체결 결과 목록 조회에 성공한다")
    void getExecutions_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Instant now = Instant.now();

        ExecutionResponse response =
                new ExecutionResponse(
                        executionId,
                        orderId,
                        2,
                        new BigDecimal("69800"),
                        139_600L,
                        2,
                        now,
                        now
                );

        when(executionQueryService.findExecutionsByUserId(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(
                new PageImpl<>(
                        List.of(response),
                        PageRequest.of(0, 10),
                        1
                )
        );

        // when & then
        mockMvc.perform(
                        get("/api/v1/executions")
                                .header(
                                        "X-User-Id",
                                        userId.toString()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success")
                        .value(true))
                .andExpect(jsonPath(
                        "$.data.content[0].executionId"
                ).value(executionId.toString()))
                .andExpect(jsonPath(
                        "$.data.content[0].orderId"
                ).value(orderId.toString()))
                .andExpect(jsonPath(
                        "$.data.content[0].executedQuantity"
                ).value(2))
                .andExpect(jsonPath(
                        "$.data.content[0].averageExecutionPrice"
                ).value(69800))
                .andExpect(jsonPath(
                        "$.data.content[0].totalExecutionAmount"
                ).value(139600))
                .andExpect(jsonPath(
                        "$.data.content[0].remainingQuantity"
                ).value(2))
                .andExpect(jsonPath("$.data.page")
                        .value(0))
                .andExpect(jsonPath("$.data.totalElements")
                        .value(1));
    }

    @Test
    @DisplayName("체결 결과가 없으면 빈 페이지를 반환한다")
    void getExecutions_empty() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        PageRequest pageable =
                PageRequest.of(0, 10);

        when(executionQueryService.findExecutionsByUserId(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(Page.empty(pageable));

        // when & then
        mockMvc.perform(
                        get("/api/v1/executions")
                                .header(
                                        "X-User-Id",
                                        userId.toString()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success")
                        .value(true))
                .andExpect(jsonPath("$.data.content")
                        .isEmpty())
                .andExpect(jsonPath("$.data.totalElements")
                        .value(0))
                .andExpect(jsonPath("$.data.totalPages")
                        .value(0));
    }

    @Test
    @DisplayName("체결 결과 상세 조회에 성공한다")
    void getExecutionDetail_success() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Instant now = Instant.now();

        ExecutionResponse response =
                new ExecutionResponse(
                        executionId,
                        orderId,
                        4,
                        new BigDecimal("70000"),
                        280_000L,
                        0,
                        now,
                        now
                );

        when(executionQueryService.findExecutionByIdAndUserId(
                executionId,
                userId
        )).thenReturn(response);

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/executions/{executionId}",
                                executionId
                        )
                                .header(
                                        "X-User-Id",
                                        userId.toString()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success")
                        .value(true))
                .andExpect(jsonPath("$.data.executionId")
                        .value(executionId.toString()))
                .andExpect(jsonPath("$.data.orderId")
                        .value(orderId.toString()))
                .andExpect(jsonPath("$.data.executedQuantity")
                        .value(4))
                .andExpect(jsonPath("$.data.remainingQuantity")
                        .value(0));
    }

    @Test
    @DisplayName("체결 결과를 찾을 수 없으면 404를 반환한다")
    void getExecutionDetail_notFound() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        when(executionQueryService.findExecutionByIdAndUserId(
                executionId,
                userId
        )).thenThrow(
                new BusinessException(
                        TradingErrorCode.EXECUTION_NOT_FOUND
                )
        );

        // when & then
        mockMvc.perform(
                        get(
                                "/api/v1/executions/{executionId}",
                                executionId
                        )
                                .header(
                                        "X-User-Id",
                                        userId.toString()
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success")
                        .value(false))
                .andExpect(jsonPath("$.errorCode")
                        .value("AUTO_TRADING_0022"));
    }

    @Test
    @DisplayName("체결 목록 조회 시 허용되지 않은 페이지 크기는 기본값 10으로 보정한다")
    void getExecutions_invalidSizeFallbackTo10() throws Exception {
        // given
        UUID userId = UUID.randomUUID();

        when(executionQueryService.findExecutionsByUserId(
                eq(userId),
                any(Pageable.class)
        )).thenReturn(
                Page.empty(
                        PageRequest.of(0, 10)
                )
        );

        // when
        mockMvc.perform(
                        get("/api/v1/executions")
                                .header(
                                        "X-User-Id",
                                        userId.toString()
                                )
                                .param("size", "20")
                )
                .andExpect(status().isOk());

        // then
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(executionQueryService)
                .findExecutionsByUserId(
                        eq(userId),
                        pageableCaptor.capture()
                );

        Pageable pageable =
                pageableCaptor.getValue();

        assertThat(pageable.getPageSize())
                .isEqualTo(10);
    }

    @Test
    @DisplayName("X-User-Id 헤더 없이 체결 목록을 조회하면 400을 반환한다")
    void getExecutions_withoutUserIdHeader() throws Exception {
        // when & then
        mockMvc.perform(
                        get("/api/v1/executions")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success")
                        .value(false))
                .andExpect(jsonPath("$.errorCode")
                        .value("COMMON_0001"));
    }
}
