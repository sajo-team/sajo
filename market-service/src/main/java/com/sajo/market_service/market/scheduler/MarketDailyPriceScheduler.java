package com.sajo.market_service.market.scheduler;

import com.sajo.market_service.market.config.MarketSchedulerProperties;
import com.sajo.market_service.market.repository.query.MarketStockCollectionTarget;
import com.sajo.market_service.market.repository.query.MarketStockQueryRepository;
import com.sajo.market_service.market.service.command.MarketStockPriceCommandService;
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

/**
 * 기존 수집 과정을 조정할 뿐, KIS 호출이나 저장 로직을 직접 담당하지 않는다.
 * */
@Component
@RequiredArgsConstructor
@Slf4j
public class MarketDailyPriceScheduler {

    private static final String STOCK_CODE_SORT_PROPERTY = "stockCode";

    private final MarketSchedulerProperties properties;
    private final MarketStockQueryRepository marketStockQueryRepository;
    private final MarketStockPriceCommandService marketStockPriceCommandService;
    private final Clock marketSchedulerClock;

    @Scheduled(cron = "${sajo.scheduler.daily-price-cron:0 10 16 * * MON-FRI}", zone = "Asia/Seoul")
    public void scheduleDailyPriceCollection() {
        collectDailyPrices();
    }

    DailyPriceCollectionSummary collectDailyPrices() {
        if (!properties.enabled()) {
            log.debug("일별 시세 Scheduler가 비활성화되어 실행을 건너뜁니다.");
            return DailyPriceCollectionSummary.notExecuted(SchedulerRunStatus.DISABLED);
        }

        SystemUserIdResolution systemUserIdResolution = resolveSystemUserId();
        if (systemUserIdResolution.status() != SchedulerRunStatus.COMPLETED) {
            return DailyPriceCollectionSummary.notExecuted(systemUserIdResolution.status());
        }

        LocalDate collectionDate = LocalDate.now(marketSchedulerClock);
        if (isWeekend(collectionDate)) {
            log.info("주말에는 일별 시세 수집을 건너뜁니다. collectionDate={}", collectionDate);
            return DailyPriceCollectionSummary.notExecuted(SchedulerRunStatus.WEEKEND);
        }

        DailyPriceCollectionSummary summary = collectStocks(systemUserIdResolution.systemUserId(), collectionDate);
        log.info("일별 시세 수집을 완료했습니다. collectionDate={}, successCount={}, failureCount={}, skippedStockCount={}",
                collectionDate, summary.successCount(), summary.failureCount(), summary.skippedStockCount());
        return summary;
    }

    private DailyPriceCollectionSummary collectStocks(UUID systemUserId, LocalDate collectionDate) {
        // KIS가 휴장일에 빈 일별 시세를 반환하면 기존 Step 6 Command가 정상 무수집으로 처리한다.
        // TODO: 멀티 인스턴스에서는 DB 중복은 Step 6 제약으로 막히지만 KIS 호출 중복은 가능하다.
        //       분산 Scheduler lock 정책이 확정되면 이 실행 경계를 보호한다.
        //       수집 중 stockCode가 cursor보다 작은 값으로 바뀌거나 새로 추가되면 다음 실행으로 이월될 수 있다.
        DailyPriceCollectionSummary summary = DailyPriceCollectionSummary.empty();
        String lastStockCode = null;
        while (true) {
            var targets = marketStockQueryRepository.findCollectionTargetsAfterStockCode(
                    lastStockCode, PageRequest.of(0, properties.pageSize(), Sort.by(Sort.Direction.ASC, STOCK_CODE_SORT_PROPERTY)));
            if (targets.isEmpty()) {
                return summary;
            }
            for (MarketStockCollectionTarget target : targets) {
                summary = collectStock(systemUserId, collectionDate, target, summary);
            }

            String nextLastStockCode = targets.getLast().getStockCode();
            if (nextLastStockCode == null
                    || (lastStockCode != null && nextLastStockCode.compareTo(lastStockCode) <= 0)) {
                log.warn("종목 수집 cursor가 진행되지 않아 이후 페이지를 중단합니다.");
                return summary;
            }
            if (targets.size() < properties.pageSize()) {
                return summary;
            }
            lastStockCode = nextLastStockCode;
        }
    }

    private DailyPriceCollectionSummary collectStock(
            UUID systemUserId,
            LocalDate collectionDate,
            MarketStockCollectionTarget target,
            DailyPriceCollectionSummary summary
    ) {
        String stockCode = target.getStockCode();
        if (stockCode == null || stockCode.isBlank()) {
            log.warn("종목 코드가 없어 일별 시세 수집을 건너뜁니다.");
            return summary.incrementSkippedStock();
        }

        try {
            marketStockPriceCommandService.collectAndSaveDailyPricesForIdentifiedStock(
                    systemUserId, target.getStockId(), stockCode, collectionDate, collectionDate);
            return summary.incrementSuccess();
        } catch (Exception exception) {
            log.warn("일별 시세 수집에 실패했습니다. stockCode={}, exceptionType={}",
                    stockCode, exception.getClass().getSimpleName());
            return summary.incrementFailure();
        }
    }

    private SystemUserIdResolution resolveSystemUserId() {
        String configuredUserId = properties.systemUserId();
        if (configuredUserId == null || configuredUserId.isBlank()) {
            log.warn("일별 시세 Scheduler 시스템 사용자 설정이 없어 실행을 건너뜁니다.");
            return new SystemUserIdResolution(null, SchedulerRunStatus.SYSTEM_USER_ID_MISSING);
        }
        try {
            return new SystemUserIdResolution(UUID.fromString(configuredUserId.trim()), SchedulerRunStatus.COMPLETED);
        } catch (IllegalArgumentException exception) {
            log.warn("일별 시세 Scheduler 시스템 사용자 설정 형식이 올바르지 않아 실행을 건너뜁니다.");
            return new SystemUserIdResolution(null, SchedulerRunStatus.SYSTEM_USER_ID_INVALID);
        }
    }

    private boolean isWeekend(LocalDate collectionDate) {
        return collectionDate.getDayOfWeek() == DayOfWeek.SATURDAY
                || collectionDate.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    enum SchedulerRunStatus {
        COMPLETED,
        DISABLED,
        SYSTEM_USER_ID_MISSING,
        SYSTEM_USER_ID_INVALID,
        WEEKEND
    }

    record SystemUserIdResolution(UUID systemUserId, SchedulerRunStatus status) {
    }

    record DailyPriceCollectionSummary(
            SchedulerRunStatus runStatus,
            int successCount,
            int failureCount,
            int skippedStockCount
    ) {

        static DailyPriceCollectionSummary empty() {
            return new DailyPriceCollectionSummary(SchedulerRunStatus.COMPLETED, 0, 0, 0);
        }

        static DailyPriceCollectionSummary notExecuted(SchedulerRunStatus runStatus) {
            return new DailyPriceCollectionSummary(runStatus, 0, 0, 0);
        }

        DailyPriceCollectionSummary incrementSuccess() {
            return new DailyPriceCollectionSummary(runStatus, successCount + 1, failureCount, skippedStockCount);
        }

        DailyPriceCollectionSummary incrementFailure() {
            return new DailyPriceCollectionSummary(runStatus, successCount, failureCount + 1, skippedStockCount);
        }

        DailyPriceCollectionSummary incrementSkippedStock() {
            return new DailyPriceCollectionSummary(runStatus, successCount, failureCount, skippedStockCount + 1);
        }
    }
}
