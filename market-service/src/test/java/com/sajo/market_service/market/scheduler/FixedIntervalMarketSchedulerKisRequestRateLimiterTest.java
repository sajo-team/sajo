package com.sajo.market_service.market.scheduler;

import com.sajo.market_service.market.config.MarketSchedulerProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

class FixedIntervalMarketSchedulerKisRequestRateLimiterTest {

    @Test
    void allowsFirstRequestWithoutWaitingAndWaitsAgainAfterAnEarlyWakeup() {
        AtomicLong now = new AtomicLong(0L);
        ArrayDeque<Long> waits = new ArrayDeque<>();
        FixedIntervalMarketSchedulerKisRequestRateLimiter limiter = limiter(now, waits);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(waits).isEmpty();

        now.set(100L);
        assertThat(limiter.tryAcquire()).isTrue();

        assertThat(waits).containsExactly(499_999_900L, 499_999_900L);
    }

    @Test
    void preservesInterruptFlagAndDoesNotGrantPermit() {
        FixedIntervalMarketSchedulerKisRequestRateLimiter limiter = limiter(new AtomicLong(), new ArrayDeque<>());
        Thread.currentThread().interrupt();
        try {
            assertThat(limiter.tryAcquire()).isFalse();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptDuringActualWaitStopsPermitAcquisitionAndPreservesFlag() throws Exception {
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicReference<Boolean> acquired = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        FixedIntervalMarketSchedulerKisRequestRateLimiter limiter = new FixedIntervalMarketSchedulerKisRequestRateLimiter(
                new MarketSchedulerProperties(false, "", "", 10, false, "", Duration.ofSeconds(30)),
                System::nanoTime,
                nanos -> {
                    waiting.countDown();
                    LockSupport.parkNanos(nanos);
                });

        assertThat(limiter.tryAcquire()).isTrue();
        Thread waitingThread = new Thread(() -> {
            acquired.set(limiter.tryAcquire());
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        waitingThread.start();

        assertThat(waiting.await(1, TimeUnit.SECONDS)).isTrue();
        waitingThread.interrupt();
        waitingThread.join(1_000);

        assertThat(waitingThread.isAlive()).isFalse();
        assertThat(acquired).hasValue(false);
        assertThat(interrupted).hasValue(true);
    }

    private FixedIntervalMarketSchedulerKisRequestRateLimiter limiter(AtomicLong now, ArrayDeque<Long> waits) {
        return new FixedIntervalMarketSchedulerKisRequestRateLimiter(
                new MarketSchedulerProperties(false, "", "", 10, false, "", Duration.ofMillis(500)),
                now::get,
                requestedWait -> {
                    waits.add(requestedWait);
                    now.addAndGet(requestedWait == 499_999_900L ? 0L : requestedWait);
                    if (waits.size() == 2) {
                        now.set(500_000_000L);
                    }
                });
    }
}
