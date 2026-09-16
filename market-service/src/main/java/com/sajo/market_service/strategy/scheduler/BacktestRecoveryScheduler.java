package com.sajo.market_service.strategy.scheduler;

import com.sajo.market_service.strategy.domain.Backtest;
import com.sajo.market_service.strategy.domain.BacktestStatus;
import com.sajo.market_service.strategy.repository.command.BacktestCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BacktestRecoveryScheduler {

    private static final long STABLE_RUNNING_THRESHOLD_MINUTES = 30;
    private static final List<BacktestStatus> STALE_TARGET_STATUSES = List.of(BacktestStatus.REQUESTED, BacktestStatus.RUNNING);

    private final BacktestCommandRepository backtestCommandRepository;

    @Transactional
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void failStaleRunningBacktest() {
        Instant threshold = Instant.now().minus(STABLE_RUNNING_THRESHOLD_MINUTES, ChronoUnit.MINUTES);

        List<Backtest> staleBacktests = backtestCommandRepository.findByStatusInAndUpdatedAtBefore(STALE_TARGET_STATUSES, threshold);

        for (Backtest backtest : staleBacktests) {
            log.warn("장기간 {} 상태로 방치된 백테스트를 FAILED 처리합니다.\n backtestId={}", backtest.getStatus(), backtest.getId());
            backtest.fail();
        }

        if (!staleBacktests.isEmpty()) {
            backtestCommandRepository.saveAll(staleBacktests);
        }
    }
}
