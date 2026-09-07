package com.sajo.market_service.market.scheduler;

import com.sajo.market_service.market.config.MarketSchedulerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * JVM-local KIS request limiter shared by the daily-price and indicator schedulers.
 *
 * <p>It intentionally does not coordinate multiple application instances. Operations must run one
 * scheduler-active instance per system App Key until distributed rate limiting is introduced.</p>
 */
@Component
public class FixedIntervalMarketSchedulerKisRequestRateLimiter implements MarketSchedulerKisRequestRateLimiter {

    private final MarketSchedulerProperties properties;
    private final LongSupplier nanoTimeSupplier;
    private final LongConsumer parker;
    private long nextAllowedRequestNanos;

    @Autowired
    public FixedIntervalMarketSchedulerKisRequestRateLimiter(MarketSchedulerProperties properties) {
        this(properties, System::nanoTime, LockSupport::parkNanos);
    }

    FixedIntervalMarketSchedulerKisRequestRateLimiter(
            MarketSchedulerProperties properties, LongSupplier nanoTimeSupplier, LongConsumer parker
    ) {
        this.properties = properties;
        this.nanoTimeSupplier = nanoTimeSupplier;
        this.parker = parker;
    }

    @Override
    public synchronized boolean tryAcquire() {
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }

            long remainingNanos = nextAllowedRequestNanos - nanoTimeSupplier.getAsLong();
            if (remainingNanos <= 0) {
                nextAllowedRequestNanos = nanoTimeSupplier.getAsLong()
                        + properties.kisRequestInterval().toNanos();
                return true;
            }

            parker.accept(remainingNanos);
        }
    }
}
