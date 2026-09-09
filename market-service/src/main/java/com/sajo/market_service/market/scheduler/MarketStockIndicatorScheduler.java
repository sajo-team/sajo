package com.sajo.market_service.market.scheduler;

import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.config.MarketSchedulerProperties;
import com.sajo.market_service.market.repository.query.MarketStockCollectionTarget;
import com.sajo.market_service.market.repository.query.MarketStockQueryRepository;
import com.sajo.market_service.market.service.command.MarketStockIndicatorCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

/** Sequentially coordinates current-price indicator snapshots; it owns neither KIS parsing nor database writes. */
@Component
@RequiredArgsConstructor
@Slf4j
public class MarketStockIndicatorScheduler {

    private static final String STOCK_CODE_SORT_PROPERTY = "stockCode";

    private final MarketSchedulerProperties properties;
    private final MarketStockQueryRepository marketStockQueryRepository;
    private final MarketStockIndicatorCommandService marketStockIndicatorCommandService;
    private final MarketSchedulerKisRequestRateLimiter requestRateLimiter;
    private final Clock marketSchedulerClock;

    @Scheduled(cron = "${sajo.scheduler.indicator-cron:0 20 16 * * MON-FRI}", zone = "Asia/Seoul")
    public void scheduleIndicatorCollection() {
        collectIndicators();
    }

    IndicatorCollectionSummary collectIndicators() {
        if (!properties.indicatorEnabled()) {
            log.debug("투자지표 Scheduler가 비활성화되어 실행하지 않습니다.");
            return IndicatorCollectionSummary.notExecuted(IndicatorRunStatus.DISABLED);
        }
        SystemUserIdResolution resolution = resolveSystemUserId();
        if (resolution.status() != IndicatorRunStatus.COMPLETED) {
            return IndicatorCollectionSummary.notExecuted(resolution.status());
        }
        LocalDate collectionDate = LocalDate.now(marketSchedulerClock);
        if (isWeekend(collectionDate)) {
            log.info("주말에는 투자지표 수집을 실행하지 않습니다. collectionDate={}", collectionDate);
            return IndicatorCollectionSummary.notExecuted(IndicatorRunStatus.WEEKEND);
        }

        UserKisTokenResponse credentials;
        try {
            credentials = marketStockIndicatorCommandService.getCollectionCredentials(resolution.userId());
        } catch (Exception exception) {
            log.warn("투자지표 Scheduler 인증정보 조회에 실패했습니다. exceptionType={}", exception.getClass().getSimpleName());
            return IndicatorCollectionSummary.notExecuted(IndicatorRunStatus.CREDENTIALS_FAILED);
        }
        if (credentials == null) {
            log.warn("투자지표 Scheduler 인증정보 조회 결과가 비어 있어 실행하지 않습니다.");
            return IndicatorCollectionSummary.notExecuted(IndicatorRunStatus.CREDENTIALS_FAILED);
        }
        IndicatorCollectionSummary summary = collectStocks(credentials);
        log.info("투자지표 수집을 완료했습니다. processedSuccessCount={}, failureCount={}, skippedStockCount={}",
                summary.processedSuccessCount(), summary.failureCount(), summary.skippedStockCount());
        return summary;
    }

    private IndicatorCollectionSummary collectStocks(UserKisTokenResponse credentials) {
        // TODO: multiple Market instances duplicate User Service and KIS calls until a distributed scheduler lock is agreed.
        IndicatorCollectionSummary summary = IndicatorCollectionSummary.empty();
        String lastStockCode = null;
        while (true) {
            var targets = properties.targetStockCodes().isEmpty()
                    ? marketStockQueryRepository.findCollectionTargetsAfterStockCode(
                    lastStockCode, PageRequest.of(0, properties.pageSize(), Sort.by(Sort.Direction.ASC, STOCK_CODE_SORT_PROPERTY)))
                    : marketStockQueryRepository.findCollectionTargetsByStockCodes(properties.targetStockCodes());
            if (!properties.targetStockCodes().isEmpty()) {
                return collectTargetList(credentials, targets, summary);
            }
            if (targets.isEmpty()) {
                return summary;
            }
            for (MarketStockCollectionTarget target : targets) {
                summary = collectStock(credentials, target, summary);
                if (summary.runStatus() == IndicatorRunStatus.INTERRUPTED) {
                    log.warn("투자지표 수집이 interrupt되어 이후 종목 처리를 중단합니다.");
                    return summary;
                }
            }
            String nextLastStockCode = targets.getLast().getStockCode();
            if (nextLastStockCode == null
                    || (lastStockCode != null && nextLastStockCode.compareTo(lastStockCode) <= 0)) {
                log.warn("투자지표 수집 cursor가 진행되지 않아 이후 페이지를 중단합니다.");
                return summary;
            }
            if (targets.size() < properties.pageSize()) {
                return summary;
            }
            lastStockCode = nextLastStockCode;
        }
    }

    private IndicatorCollectionSummary collectTargetList(
            UserKisTokenResponse credentials,
            java.util.List<MarketStockCollectionTarget> targets,
            IndicatorCollectionSummary summary
    ) {
        for (MarketStockCollectionTarget target : targets) {
            summary = collectStock(credentials, target, summary);
            if (summary.runStatus() == IndicatorRunStatus.INTERRUPTED) {
                return summary;
            }
        }
        return summary;
    }

    private IndicatorCollectionSummary collectStock(
            UserKisTokenResponse credentials,
            MarketStockCollectionTarget target,
            IndicatorCollectionSummary summary
    ) {
        if (target.getStockId() == null || target.getStockCode() == null || target.getStockCode().isBlank()) {
            log.warn("종목 식별 정보가 없어 투자지표 수집을 건너뜁니다.");
            return summary.incrementSkippedStock();
        }
        try {
            if (!requestRateLimiter.tryAcquire()) {
                return summary.interrupted();
            }
            boolean stored = marketStockIndicatorCommandService.collectAndSaveIndicatorsForIdentifiedStock(
                    credentials, target.getStockId(), target.getStockCode());
            return stored ? summary.incrementSuccess() : summary.incrementSkippedStock();
        } catch (Exception exception) {
            log.warn("투자지표 수집에 실패했습니다. stockCode={}, exceptionType={}",
                    target.getStockCode(), exception.getClass().getSimpleName());
            return summary.incrementFailure();
        }
    }

    private SystemUserIdResolution resolveSystemUserId() {
        String configuredUserId = properties.systemUserId();
        if (configuredUserId == null || configuredUserId.isBlank()) {
            log.warn("투자지표 Scheduler 시스템 사용자 설정이 없어 실행하지 않습니다.");
            return new SystemUserIdResolution(null, IndicatorRunStatus.SYSTEM_USER_ID_MISSING);
        }
        try {
            return new SystemUserIdResolution(UUID.fromString(configuredUserId.trim()), IndicatorRunStatus.COMPLETED);
        } catch (IllegalArgumentException exception) {
            log.warn("투자지표 Scheduler 시스템 사용자 설정 형식이 올바르지 않아 실행하지 않습니다.");
            return new SystemUserIdResolution(null, IndicatorRunStatus.SYSTEM_USER_ID_INVALID);
        }
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    enum IndicatorRunStatus { COMPLETED, DISABLED, SYSTEM_USER_ID_MISSING, SYSTEM_USER_ID_INVALID, CREDENTIALS_FAILED, WEEKEND, INTERRUPTED }

    record SystemUserIdResolution(UUID userId, IndicatorRunStatus status) { }

    record IndicatorCollectionSummary(IndicatorRunStatus runStatus, int processedSuccessCount, int failureCount, int skippedStockCount) {
        static IndicatorCollectionSummary empty() { return new IndicatorCollectionSummary(IndicatorRunStatus.COMPLETED, 0, 0, 0); }
        static IndicatorCollectionSummary notExecuted(IndicatorRunStatus status) { return new IndicatorCollectionSummary(status, 0, 0, 0); }
        IndicatorCollectionSummary incrementSuccess() { return new IndicatorCollectionSummary(runStatus, processedSuccessCount + 1, failureCount, skippedStockCount); }
        IndicatorCollectionSummary incrementFailure() { return new IndicatorCollectionSummary(runStatus, processedSuccessCount, failureCount + 1, skippedStockCount); }
        IndicatorCollectionSummary incrementSkippedStock() { return new IndicatorCollectionSummary(runStatus, processedSuccessCount, failureCount, skippedStockCount + 1); }
        IndicatorCollectionSummary interrupted() { return new IndicatorCollectionSummary(IndicatorRunStatus.INTERRUPTED, processedSuccessCount, failureCount, skippedStockCount); }
    }
}
