package com.sajo.market_service.market.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@code marketQuoteRequestEventPublishExecutor}가 큐 포화 시 예외를 던지는 대신 조용히
 * 스킵하는지 검증한다(코드 리뷰 반영, #248). 이 보장이 깨지면 현재가 조회 API가 고부하 상황에서
 * RejectedExecutionException으로 응답 자체가 실패할 수 있다.
 */
class MarketQuoteRequestEventProducerConfigTest {

    private static final int TASKS_BEYOND_CAPACITY = 20_000;

    private final MarketQuoteRequestEventProducerConfig config = new MarketQuoteRequestEventProducerConfig();
    private ExecutorService publishExecutor;

    @AfterEach
    void tearDown() {
        if (publishExecutor != null) {
            publishExecutor.shutdownNow();
        }
    }

    @Test
    void rejectedTaskIsDroppedWithoutThrowingWhenQueueIsSaturated() throws InterruptedException {
        publishExecutor = (ExecutorService) config.marketQuoteRequestEventPublishExecutor();

        // 유일한 워커 스레드를 블로킹시켜, 이후 제출되는 태스크가 전부 큐에 쌓이도록 만든다.
        CountDownLatch workerBlocked = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        publishExecutor.execute(() -> {
            workerBlocked.countDown();
            awaitUninterruptibly(releaseWorker);
        });
        assertThatCode(() -> workerBlocked.await(5, TimeUnit.SECONDS)).doesNotThrowAnyException();

        // 큐 용량(구현상 10_000)을 크게 웃도는 태스크를 제출해 강제로 포화 상태를 만든다 — 정확한
        // 상수값에 결합되지 않도록 여유 있게 잡는다.
        try {
            assertThatCode(() -> {
                for (int i = 0; i < TASKS_BEYOND_CAPACITY; i++) {
                    publishExecutor.execute(() -> { });
                }
            }).doesNotThrowAnyException();
        } finally {
            releaseWorker.countDown();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    latch.await();
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
