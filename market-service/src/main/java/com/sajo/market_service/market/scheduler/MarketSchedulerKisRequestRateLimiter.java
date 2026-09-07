package com.sajo.market_service.market.scheduler;

/** Coordinates KIS calls from all market schedulers within one application instance. */
public interface MarketSchedulerKisRequestRateLimiter {

    /**
     * Waits until the next KIS request is allowed.
     *
     * @return {@code false} when the scheduler thread is interrupted before it receives a permit
     */
    boolean tryAcquire();
}
