package com.sajo.market_service.market.scheduler;

import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.config.MarketSchedulerProperties;
import com.sajo.market_service.market.repository.query.MarketStockCollectionTarget;
import com.sajo.market_service.market.repository.query.MarketStockQueryRepository;
import com.sajo.market_service.market.service.command.MarketStockIndicatorCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketStockIndicatorSchedulerTest {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    @Mock private MarketStockQueryRepository stockRepository;
    @Mock private MarketStockIndicatorCommandService commandService;
    @Mock private MarketSchedulerKisRequestRateLimiter rateLimiter;

    @Test
    void doesNotRunWhenDisabled() {
        var scheduler = scheduler(false, USER_ID.toString(), fridayClock());
        assertThat(scheduler.collectIndicators().runStatus()).isEqualTo(MarketStockIndicatorScheduler.IndicatorRunStatus.DISABLED);
        verify(stockRepository, never()).findCollectionTargetsAfterStockCode(any(), any());
    }

    @Test
    void usesConfiguredTargetCodesInsteadOfKeysetPagination() {
        MarketStockCollectionTarget target = target("005930");
        var scheduler = scheduler(true, USER_ID.toString(), fridayClock(), List.of("005930"));
        when(commandService.getCollectionCredentials(USER_ID)).thenReturn(new UserKisTokenResponse("token", "key", "secret"));
        when(stockRepository.findCollectionTargetsByStockCodes(List.of("005930"))).thenReturn(List.of(target));

        scheduler.collectIndicators();

        verify(stockRepository).findCollectionTargetsByStockCodes(List.of("005930"));
        verify(stockRepository, never()).findCollectionTargetsAfterStockCode(any(), any());
    }

    @Test
    void doesNotRunWhenSystemUserIdIsMissingOrInvalid() {
        assertThat(scheduler(true, " ", fridayClock()).collectIndicators().runStatus())
                .isEqualTo(MarketStockIndicatorScheduler.IndicatorRunStatus.SYSTEM_USER_ID_MISSING);
        assertThat(scheduler(true, "invalid", fridayClock()).collectIndicators().runStatus())
                .isEqualTo(MarketStockIndicatorScheduler.IndicatorRunStatus.SYSTEM_USER_ID_INVALID);
        verify(stockRepository, never()).findCollectionTargetsAfterStockCode(any(), any());
    }

    @Test
    void doesNotRunOnWeekend() {
        Clock saturday = Clock.fixed(Instant.parse("2026-09-05T08:00:00Z"), ZoneId.of("Asia/Seoul"));
        assertThat(scheduler(true, USER_ID.toString(), saturday).collectIndicators().runStatus())
                .isEqualTo(MarketStockIndicatorScheduler.IndicatorRunStatus.WEEKEND);
        verify(commandService, never()).getCollectionCredentials(any());
    }

    @Test
    void fetchesCredentialsOnceAndProcessesKeysetTargetsWithRateLimiter() {
        var scheduler = scheduler(true, USER_ID.toString(), fridayClock());
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "key", "secret");
        MarketStockCollectionTarget target = target("005930");
        when(commandService.getCollectionCredentials(USER_ID)).thenReturn(credentials);
        when(stockRepository.findCollectionTargetsAfterStockCode(isNull(), any(Pageable.class))).thenReturn(List.of(target));
        when(commandService.collectAndSaveIndicatorsForIdentifiedStock(credentials, target.getStockId(), "005930")).thenReturn(true);

        var summary = scheduler.collectIndicators();

        assertThat(summary.processedSuccessCount()).isEqualTo(1);
        verify(commandService).getCollectionCredentials(USER_ID);
        verify(rateLimiter).tryAcquire();
        verify(commandService).collectAndSaveIndicatorsForIdentifiedStock(credentials, target.getStockId(), "005930");
    }

    @Test
    void continuesAfterOneStockFails() {
        var scheduler = scheduler(true, USER_ID.toString(), fridayClock());
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "key", "secret");
        MarketStockCollectionTarget failed = target("000660");
        MarketStockCollectionTarget succeeded = target("005930");
        when(commandService.getCollectionCredentials(USER_ID)).thenReturn(credentials);
        when(stockRepository.findCollectionTargetsAfterStockCode(isNull(), any(Pageable.class))).thenReturn(List.of(failed, succeeded));
        org.mockito.Mockito.doThrow(new IllegalStateException()).when(commandService)
                .collectAndSaveIndicatorsForIdentifiedStock(credentials, failed.getStockId(), "000660");
        when(commandService.collectAndSaveIndicatorsForIdentifiedStock(credentials, succeeded.getStockId(), "005930")).thenReturn(true);

        var summary = scheduler.collectIndicators();

        assertThat(summary.failureCount()).isEqualTo(1);
        assertThat(summary.processedSuccessCount()).isEqualTo(1);
        verify(commandService, times(1)).collectAndSaveIndicatorsForIdentifiedStock(credentials, succeeded.getStockId(), "005930");
    }

    @Test
    void stopsWhenCursorDoesNotAdvance() {
        var scheduler = scheduler(true, USER_ID.toString(), fridayClock());
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "key", "secret");
        MarketStockCollectionTarget first = target("005930");
        when(commandService.getCollectionCredentials(USER_ID)).thenReturn(credentials);
        when(stockRepository.findCollectionTargetsAfterStockCode(isNull(), any(Pageable.class))).thenReturn(Collections.nCopies(10, first));
        when(stockRepository.findCollectionTargetsAfterStockCode(eq("005930"), any(Pageable.class))).thenReturn(List.of(first));

        scheduler.collectIndicators();

        verify(stockRepository, times(2)).findCollectionTargetsAfterStockCode(any(), any());
    }

    @Test
    void countsAnUnstorableSnapshotAsSkipped() {
        var scheduler = scheduler(true, USER_ID.toString(), fridayClock());
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "key", "secret");
        MarketStockCollectionTarget target = target("005930");
        when(commandService.getCollectionCredentials(USER_ID)).thenReturn(credentials);
        when(stockRepository.findCollectionTargetsAfterStockCode(isNull(), any(Pageable.class))).thenReturn(List.of(target));
        when(commandService.collectAndSaveIndicatorsForIdentifiedStock(credentials, target.getStockId(), "005930")).thenReturn(false);

        var summary = scheduler.collectIndicators();

        assertThat(summary.processedSuccessCount()).isZero();
        assertThat(summary.skippedStockCount()).isEqualTo(1);
        assertThat(summary.failureCount()).isZero();
    }

    @Test
    void stopsBeforeStockLookupWhenCredentialLookupFails() {
        var scheduler = scheduler(true, USER_ID.toString(), fridayClock());
        when(commandService.getCollectionCredentials(USER_ID)).thenThrow(new IllegalStateException("credential failure"));

        var summary = scheduler.collectIndicators();

        assertThat(summary.runStatus()).isEqualTo(MarketStockIndicatorScheduler.IndicatorRunStatus.CREDENTIALS_FAILED);
        verify(stockRepository, never()).findCollectionTargetsAfterStockCode(any(), any());
        verify(commandService, never()).collectAndSaveIndicatorsForIdentifiedStock(any(), any(), any());
    }

    @Test
    void stopsProcessingFurtherStocksWhenRateLimiterIsInterrupted() {
        var scheduler = scheduler(true, USER_ID.toString(), fridayClock());
        UserKisTokenResponse credentials = new UserKisTokenResponse("token", "key", "secret");
        MarketStockCollectionTarget first = target("000660");
        MarketStockCollectionTarget second = target("005930");
        when(commandService.getCollectionCredentials(USER_ID)).thenReturn(credentials);
        when(stockRepository.findCollectionTargetsAfterStockCode(isNull(), any(Pageable.class))).thenReturn(List.of(first, second));
        when(rateLimiter.tryAcquire()).thenReturn(false);

        var summary = scheduler.collectIndicators();

        assertThat(summary.runStatus()).isEqualTo(MarketStockIndicatorScheduler.IndicatorRunStatus.INTERRUPTED);
        verify(commandService, never()).collectAndSaveIndicatorsForIdentifiedStock(any(), any(), any());
        verify(rateLimiter).tryAcquire();
    }

    @Test
    void schedulerDoesNotDeclareTransaction() throws Exception {
        Method method = MarketStockIndicatorScheduler.class.getMethod("scheduleIndicatorCollection");
        assertThat(method.isAnnotationPresent(Transactional.class)).isFalse();
    }

    private MarketStockIndicatorScheduler scheduler(boolean enabled, String userId, Clock clock) {
        return scheduler(enabled, userId, clock, List.of());
    }

    private MarketStockIndicatorScheduler scheduler(boolean enabled, String userId, Clock clock,
                                                    List<String> targetStockCodes) {
        lenient().when(rateLimiter.tryAcquire()).thenReturn(true);
        return new MarketStockIndicatorScheduler(new MarketSchedulerProperties(
                false, userId, "0 10 16 * * MON-FRI", 10, enabled, "0 20 16 * * MON-FRI", java.time.Duration.ofMillis(500), targetStockCodes),
                stockRepository, commandService, rateLimiter, clock);
    }

    private Clock fridayClock() { return Clock.fixed(Instant.parse("2026-09-04T08:00:00Z"), ZoneId.of("Asia/Seoul")); }

    private MarketStockCollectionTarget target(String stockCode) {
        UUID stockId = UUID.randomUUID();
        return new MarketStockCollectionTarget() {
            public UUID getStockId() { return stockId; }
            public String getStockCode() { return stockCode; }
        };
    }
}
