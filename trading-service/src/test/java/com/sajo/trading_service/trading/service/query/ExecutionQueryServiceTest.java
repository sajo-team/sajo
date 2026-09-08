package com.sajo.trading_service.trading.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.response.ExecutionResponse;
import com.sajo.trading_service.trading.domain.Execution;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.query.ExecutionQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionQueryServiceTest {

    @Mock
    private ExecutionQueryRepository executionQueryRepository;

    private ExecutionQueryService executionQueryService;

    @BeforeEach
    void setUp() {
        executionQueryService =
                new ExecutionQueryService(
                        executionQueryRepository
                );
    }

    @Test
    @DisplayName("사용자의 체결 결과 목록을 조회한다")
    void findExecutionsByUserId_success() {
        // given
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Execution execution =
                createExecution(orderId);

        PageRequest pageable =
                PageRequest.of(0, 10);

        when(executionQueryRepository.findByUserId(
                userId,
                pageable
        )).thenReturn(
                new PageImpl<>(
                        List.of(execution),
                        pageable,
                        1
                )
        );

        // when
        Page<ExecutionResponse> result =
                executionQueryService.findExecutionsByUserId(
                        userId,
                        pageable
                );

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);

        ExecutionResponse response =
                result.getContent().get(0);

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.executedQuantity()).isEqualTo(2);
        assertThat(response.averageExecutionPrice())
                .isEqualTo(69_800L);
        assertThat(response.totalExecutionAmount())
                .isEqualTo(139_600L);
        assertThat(response.remainingQuantity()).isEqualTo(2);

        verify(executionQueryRepository)
                .findByUserId(
                        userId,
                        pageable
                );
    }

    @Test
    @DisplayName("사용자의 체결 결과 상세를 조회한다")
    void findExecutionByIdAndUserId_success() {
        // given
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        Execution execution =
                createExecution(orderId);

        ReflectionTestUtils.setField(
                execution,
                "id",
                executionId
        );

        when(executionQueryRepository.findByIdAndUserId(
                executionId,
                userId
        )).thenReturn(Optional.of(execution));

        // when
        ExecutionResponse response =
                executionQueryService.findExecutionByIdAndUserId(
                        executionId,
                        userId
                );

        // then
        assertThat(response.executionId())
                .isEqualTo(executionId);
        assertThat(response.orderId())
                .isEqualTo(orderId);
        assertThat(response.executedQuantity())
                .isEqualTo(2);
        assertThat(response.remainingQuantity())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("사용자의 체결 결과를 찾을 수 없으면 EXECUTION_NOT_FOUND 예외가 발생한다")
    void findExecutionByIdAndUserId_notFound() {
        // given
        UUID userId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        when(executionQueryRepository.findByIdAndUserId(
                executionId,
                userId
        )).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                executionQueryService.findExecutionByIdAndUserId(
                        executionId,
                        userId
                )
        )
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(
                                ((BusinessException) exception)
                                        .getErrorCode()
                        ).isEqualTo(
                                TradingErrorCode.EXECUTION_NOT_FOUND
                        )
                );
    }

    private Execution createExecution(UUID orderId) {
        Execution execution =
                Execution.create(
                        orderId,
                        2,
                        69_800L,
                        139_600L,
                        2
                );

        Instant now = Instant.now();

        ReflectionTestUtils.setField(
                execution,
                "createdAt",
                now
        );

        ReflectionTestUtils.setField(
                execution,
                "updatedAt",
                now
        );

        return execution;
    }
}
