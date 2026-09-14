package com.sajo.trading_service.trading.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.request.ExecutionSearchCondition;
import com.sajo.trading_service.trading.controller.dto.response.ExecutionResponse;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.query.ExecutionQueryRepository;
import com.sajo.trading_service.trading.repository.query.projection.ExecutionQueryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExecutionQueryService {

    private final ExecutionQueryRepository executionQueryRepository;

    public Page<ExecutionResponse> findExecutionsByUserId(
            UUID userId,
            ExecutionSearchCondition condition,
            Pageable pageable
    ) {
        return executionQueryRepository
                .findByUserId(userId,
                        condition.orderId(),
                        condition.autoTradingId(),
                        condition.strategyId(),
                        pageable)
                .map(ExecutionResponse::from);
    }

    public ExecutionResponse findExecutionByIdAndUserId(
            UUID executionId,
            UUID userId
    ) {
        ExecutionQueryProjection projection =
                executionQueryRepository
                        .findByIdAndUserId(
                                executionId,
                                userId
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        TradingErrorCode.EXECUTION_NOT_FOUND
                                )
                        );

        return ExecutionResponse.from(projection);
    }
}