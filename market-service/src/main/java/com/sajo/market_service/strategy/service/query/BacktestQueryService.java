package com.sajo.market_service.strategy.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.strategy.controller.dto.response.BacktestDetailResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestInternalResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestListResponse;
import com.sajo.market_service.strategy.controller.dto.response.BacktestStatusResponse;
import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.exception.StrategyErrorCode;
import com.sajo.market_service.strategy.repository.query.BacktestQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BacktestQueryService {

    private final BacktestQueryRepository backtestQueryRepository;

    public BacktestStatusResponse getBacktestStatus(
            UUID userId,
            UUID strategyId,
            UUID backtestId
    ) {
        log.info("백테스트 상태 조회 시작. strategyId={}, backtestId={}", strategyId, backtestId);
        Backtest backtest = getBacktestByOwner(userId, strategyId, backtestId);

        log.info("백테스트 상태 조회 완료. strategyId={}, backtestId={}, status={}",
                strategyId, backtestId, backtest.getStatus());

        return BacktestStatusResponse.from(backtest);
    }

    public BacktestDetailResponse getBacktestDetail(
            UUID userId,
            UUID strategyId,
            UUID backtestId
    ) {
        log.info("백테스트 상세 조회 시작. strategyId={}, backtestId={}", strategyId, backtestId);
        Backtest backtest = getBacktestByOwner(userId, strategyId, backtestId);

        log.info("백테스트 상세 조회 완료. strategyId={}, backtestId={}, status={}",
                strategyId, backtestId, backtest.getStatus());

        return BacktestDetailResponse.from(backtest);
    }

    public BacktestListResponse getBacktests(
            UUID userId,
            UUID strategyId,
            Pageable pageable
    ) {
        log.info("백테스트 목록 조회 시작. strategyId={}, page={}, size={}",
                strategyId, pageable.getPageNumber(), pageable.getPageSize());

        Page<Backtest> page = backtestQueryRepository
                .findByStrategyIdAndUserIdAndDeletedAtIsNull(strategyId, userId, pageable);

        log.info("백테스트 목록 조회 완료. strategyId={}, resultCount={}, totalElements={}",
                strategyId, page.getNumberOfElements(), page.getTotalElements());

        return BacktestListResponse.from(page);
    }

    public BacktestInternalResponse getBacktestInternal(UUID backtestId) {
        log.info("내부 백테스트 조회 시작. backtestId={}", backtestId);

        Backtest backtest = backtestQueryRepository
                .findByIdAndDeletedAtIsNull(backtestId)
                .orElseThrow(() -> {
                    log.warn("내부 백테스트 조회 실패: 백테스트를 찾을 수 없습니다. backtestId={}", backtestId);
                    return new BusinessException(StrategyErrorCode.BACKTEST_NOT_FOUND);
                });

        log.info("내부 백테스트 조회 완료. backtestId={}, userId={}, status={}",
                backtestId, backtest.getUserId(), backtest.getStatus());

        return BacktestInternalResponse.from(backtest);
    }

    private Backtest getBacktestByOwner(UUID userId, UUID strategyId, UUID backtestId) {
        return backtestQueryRepository
                .findByIdAndStrategyIdAndUserIdAndDeletedAtIsNull(backtestId, strategyId, userId)
                .orElseThrow(() -> {
                    log.warn("백테스트 조회 실패: 백테스트를 찾을 수 없습니다. strategyId={}, backtestId={}",
                            strategyId, backtestId);
                    return new BusinessException(StrategyErrorCode.BACKTEST_NOT_FOUND);
                });
    }
}
