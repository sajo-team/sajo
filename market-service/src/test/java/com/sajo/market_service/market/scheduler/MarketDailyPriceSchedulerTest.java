package com.sajo.market_service.market.scheduler;

import com.sajo.market_service.market.config.MarketSchedulerProperties;
import com.sajo.market_service.market.repository.query.MarketStockCollectionTarget;
import com.sajo.market_service.market.repository.query.MarketStockQueryRepository;
import com.sajo.market_service.market.service.command.MarketStockPriceCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MarketDailyPriceSchedulerTest {

    private static final UUID SYSTEM_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 4);

    @Mock
    private MarketStockQueryRepository marketStockQueryRepository;
    @Mock
    private MarketStockPriceCommandService marketStockPriceCommandService;

    @Test
    void reportsDisabledRunWithoutCountingASkippedStock() {
        MarketDailyPriceScheduler.DailyPriceCollectionSummary summary = scheduler(false, SYSTEM_USER_ID.toString(), 1)
                .collectDailyPrices();

        assertThat(summary).isEqualTo(summary(MarketDailyPriceScheduler.SchedulerRunStatus.DISABLED, 0, 0, 0));
        verify(marketStockQueryRepository, never()).findCollectionTargetsAfterStockCode(any(), any());
    }

    @Test
    void reportsMissingSystemUserIdWithoutCountingASkippedStock() {
        MarketDailyPriceScheduler.DailyPriceCollectionSummary summary = scheduler(true, " ", 1).collectDailyPrices();

        assertThat(summary).isEqualTo(summary(MarketDailyPriceScheduler.SchedulerRunStatus.SYSTEM_USER_ID_MISSING, 0, 0, 0));
        verify(marketStockQueryRepository, never()).findCollectionTargetsAfterStockCode(any(), any());
    }

    @Test
    void reportsInvalidSystemUserIdWithoutCountingASkippedStock() {
        MarketDailyPriceScheduler.DailyPriceCollectionSummary summary = scheduler(true, "invalid-user-id", 1)
                .collectDailyPrices();

        assertThat(summary).isEqualTo(summary(MarketDailyPriceScheduler.SchedulerRunStatus.SYSTEM_USER_ID_INVALID, 0, 0, 0));
        verify(marketStockQueryRepository, never()).findCollectionTargetsAfterStockCode(any(), any());
    }

    @Test
    void reportsWeekendRunWithoutCountingASkippedStock() {
        Clock saturdayClock = Clock.fixed(Instant.parse("2026-09-05T08:00:00Z"), ZoneId.of("Asia/Seoul"));
        MarketDailyPriceScheduler scheduler = scheduler(true, SYSTEM_USER_ID.toString(), 1, saturdayClock);

        MarketDailyPriceScheduler.DailyPriceCollectionSummary summary = scheduler.collectDailyPrices();

        assertThat(summary).isEqualTo(summary(MarketDailyPriceScheduler.SchedulerRunStatus.WEEKEND, 0, 0, 0));
        verify(marketStockQueryRepository, never()).findCollectionTargetsAfterStockCode(any(), any());
    }

    @Test
    void collectsMultipleKeysetPagesUsingThePreviousLastStockCodeAsCursor() {
        MarketDailyPriceScheduler scheduler = scheduler(true, SYSTEM_USER_ID.toString(), 1);
        MarketStockCollectionTarget first = target("000660");
        MarketStockCollectionTarget second = target("005930");
        given(marketStockQueryRepository.findCollectionTargetsAfterStockCode(isNull(), any(Pageable.class)))
                .willReturn(List.of(first));
        given(marketStockQueryRepository.findCollectionTargetsAfterStockCode(eq("000660"), any(Pageable.class)))
                .willReturn(List.of(second));
        given(marketStockQueryRepository.findCollectionTargetsAfterStockCode(eq("005930"), any(Pageable.class)))
                .willReturn(List.of());

        MarketDailyPriceScheduler.DailyPriceCollectionSummary summary = scheduler.collectDailyPrices();

        assertThat(summary).isEqualTo(summary(MarketDailyPriceScheduler.SchedulerRunStatus.COMPLETED, 2, 0, 0));
        verifyCollected(first);
        verifyCollected(second);
        ArgumentCaptor<String> cursors = ArgumentCaptor.forClass(String.class);
        verify(marketStockQueryRepository, times(3))
                .findCollectionTargetsAfterStockCode(cursors.capture(), any(Pageable.class));
        assertThat(cursors.getAllValues()).containsExactly(null, "000660", "005930");
    }

    @Test
    void stopsImmediatelyWhenFirstKeysetPageIsEmpty() {
        MarketDailyPriceScheduler scheduler = scheduler(true, SYSTEM_USER_ID.toString(), 2);
        given(marketStockQueryRepository.findCollectionTargetsAfterStockCode(isNull(), any(Pageable.class)))
                .willReturn(List.of());

        MarketDailyPriceScheduler.DailyPriceCollectionSummary summary = scheduler.collectDailyPrices();

        assertThat(summary).isEqualTo(summary(MarketDailyPriceScheduler.SchedulerRunStatus.COMPLETED, 0, 0, 0));
        verify(marketStockQueryRepository).findCollectionTargetsAfterStockCode(isNull(), any(Pageable.class));
        verify(marketStockPriceCommandService, never()).collectAndSaveDailyPricesForIdentifiedStock(any(), any(), any(), any(), any());
    }

    @Test
    void stopsAfterProcessingPartialLastKeysetPage() {
        MarketDailyPriceScheduler scheduler = scheduler(true, SYSTEM_USER_ID.toString(), 2);
        MarketStockCollectionTarget target = target("005930");
        given(marketStockQueryRepository.findCollectionTargetsAfterStockCode(isNull(), any(Pageable.class)))
                .willReturn(List.of(target));

        MarketDailyPriceScheduler.DailyPriceCollectionSummary summary = scheduler.collectDailyPrices();

        assertThat(summary).isEqualTo(summary(MarketDailyPriceScheduler.SchedulerRunStatus.COMPLETED, 1, 0, 0));
        verifyCollected(target);
        verify(marketStockQueryRepository, times(1)).findCollectionTargetsAfterStockCode(any(), any());
    }

    @Test
    void continuesWithNextStockWhenOneCollectionFails() {
        MarketDailyPriceScheduler scheduler = scheduler(true, SYSTEM_USER_ID.toString(), 10);
        MarketStockCollectionTarget failed = target("000660");
        MarketStockCollectionTarget succeeded = target("005930");
        given(marketStockQueryRepository.findCollectionTargetsAfterStockCode(isNull(), any(Pageable.class)))
                .willReturn(List.of(failed, succeeded));
        given(marketStockPriceCommandService.collectAndSaveDailyPricesForIdentifiedStock(
                SYSTEM_USER_ID, failed.getStockId(), failed.getStockCode(), FRIDAY, FRIDAY))
                .willThrow(new IllegalStateException("KIS failed"));

        MarketDailyPriceScheduler.DailyPriceCollectionSummary summary = scheduler.collectDailyPrices();

        assertThat(summary).isEqualTo(summary(MarketDailyPriceScheduler.SchedulerRunStatus.COMPLETED, 1, 1, 0));
        verifyCollected(failed);
        verifyCollected(succeeded);
    }

    @Test
    void schedulerDoesNotDeclareTransaction() throws Exception {
        Method scheduledMethod = MarketDailyPriceScheduler.class.getMethod("scheduleDailyPriceCollection");
        Method collectionMethod = MarketDailyPriceScheduler.class.getDeclaredMethod("collectDailyPrices");

        assertThat(scheduledMethod.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(collectionMethod.isAnnotationPresent(Transactional.class)).isFalse();
    }

    private MarketDailyPriceScheduler scheduler(boolean enabled, String systemUserId, int pageSize) {
        return scheduler(enabled, systemUserId, pageSize, fridayClock());
    }

    private MarketDailyPriceScheduler scheduler(boolean enabled, String systemUserId, int pageSize, Clock clock) {
        return new MarketDailyPriceScheduler(
                new MarketSchedulerProperties(enabled, systemUserId, "0 10 16 * * MON-FRI", pageSize),
                marketStockQueryRepository,
                marketStockPriceCommandService,
                clock
        );
    }

    private void verifyCollected(MarketStockCollectionTarget target) {
        verify(marketStockPriceCommandService).collectAndSaveDailyPricesForIdentifiedStock(
                SYSTEM_USER_ID, target.getStockId(), target.getStockCode(), FRIDAY, FRIDAY);
    }

    private MarketDailyPriceScheduler.DailyPriceCollectionSummary summary(
            MarketDailyPriceScheduler.SchedulerRunStatus status,
            int successCount,
            int failureCount,
            int skippedStockCount
    ) {
        return new MarketDailyPriceScheduler.DailyPriceCollectionSummary(
                status, successCount, failureCount, skippedStockCount);
    }

    private Clock fridayClock() {
        return Clock.fixed(Instant.parse("2026-09-04T08:00:00Z"), ZoneId.of("Asia/Seoul"));
    }

    private MarketStockCollectionTarget target(String stockCode) {
        UUID stockId = UUID.randomUUID();
        return new MarketStockCollectionTarget() {
            @Override
            public UUID getStockId() {
                return stockId;
            }

            @Override
            public String getStockCode() {
                return stockCode;
            }
        };
    }
}
