package com.sajo.user_service.account.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// KisTokenLocalCache의 핵심 전제 두 가지(실패한 로더는 재시도 가능해야 하고, 만료된 값은 없는 것으로 취급돼야
// 한다)를 실측으로 검증한다. 둘 다 Caffeine의 AsyncCache + asMap().putIfAbsent 조합에 직접 의존하는
// 동작이라, Caffeine 내부 구현이 바뀌면 이 테스트가 가장 먼저 깨져서 알려줘야 한다.
class KisTokenLocalCacheTest {

    private static final Duration TTL = Duration.ofSeconds(10);

    private KisTokenLocalCache newCache(Ticker ticker) {
        AsyncCache<String, KisTokenEntry> asyncCache = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfter(new Expiry<String, KisTokenEntry>() {
                    @Override
                    public long expireAfterCreate(String key, KisTokenEntry entry, long currentTime) {
                        return entry.ttl().toNanos();
                    }

                    @Override
                    public long expireAfterUpdate(
                            String key, KisTokenEntry entry, long currentTime, long currentDuration) {
                        return entry.ttl().toNanos();
                    }

                    @Override
                    public long expireAfterRead(
                            String key, KisTokenEntry entry, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .buildAsync();
        return new KisTokenLocalCache(asyncCache);
    }

    @Test
    @DisplayName("같은 키로 두 번 호출하면 로더는 한 번만 실행되고, 두 번째 호출은 캐시된 값을 그대로 받는다")
    void getOrLoad_secondCall_reusesCachedValueWithoutReloading() {
        KisTokenLocalCache cache = newCache(new FakeTicker());
        AtomicInteger callCount = new AtomicInteger();
        String key = "key";

        KisTokenEntry first = cache.getOrLoad(key, () -> {
            callCount.incrementAndGet();
            return new KisTokenEntry("token", TTL);
        });
        KisTokenEntry second = cache.getOrLoad(key, () -> {
            callCount.incrementAndGet();
            return new KisTokenEntry("should-not-be-used", TTL);
        });

        assertThat(first.value()).isEqualTo("token");
        assertThat(second.value()).isEqualTo("token");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("로더가 예외를 던지면 그 예외가 그대로 전파되고, 실패한 항목은 캐시에 남지 않아 같은 키로 재요청하면 로더가 다시 실행된다")
    void getOrLoad_loaderFails_removedFromCacheAndRetriedOnNextCall() {
        KisTokenLocalCache cache = newCache(new FakeTicker());
        AtomicInteger callCount = new AtomicInteger();
        String key = "key";
        RuntimeException failure = new RuntimeException("KIS 호출 실패");

        assertThatThrownBy(() -> cache.getOrLoad(key, () -> {
            callCount.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        // 실패 직후에는 캐시에 아무것도 안 남아있어야 한다 (peek로 간접 확인)
        assertThat(cache.peek(key)).isEmpty();

        KisTokenEntry retried = cache.getOrLoad(key, () -> {
            callCount.incrementAndGet();
            return new KisTokenEntry("recovered-token", TTL);
        });

        assertThat(retried.value()).isEqualTo("recovered-token");
        assertThat(callCount.get()).isEqualTo(2); // 실패 1번 + 재시도 1번 = 로더가 2번 실행됨
    }

    @Test
    @DisplayName("TTL이 지나면 캐시된 값은 만료된 것으로 취급되어, 같은 키로 재요청하면 로더가 다시 실행된다")
    void getOrLoad_afterTtlExpires_reloadsOnNextCall() {
        FakeTicker ticker = new FakeTicker();
        KisTokenLocalCache cache = newCache(ticker);
        AtomicInteger callCount = new AtomicInteger();
        String key = "key";

        cache.getOrLoad(key, () -> {
            callCount.incrementAndGet();
            return new KisTokenEntry("first-token", TTL);
        });

        ticker.advance(TTL.plusSeconds(1)); // TTL을 넘겨서 논리적으로 만료시킴

        KisTokenEntry afterExpiry = cache.getOrLoad(key, () -> {
            callCount.incrementAndGet();
            return new KisTokenEntry("second-token", TTL);
        });

        assertThat(afterExpiry.value()).isEqualTo("second-token");
        assertThat(callCount.get()).isEqualTo(2); // 만료 전 1번 + 만료 후 재로딩 1번
    }

    @Test
    @DisplayName("evict 하면 이후 같은 키로 재요청 시 로더가 다시 실행된다")
    void evict_forcesReloadOnNextCall() {
        KisTokenLocalCache cache = newCache(new FakeTicker());
        AtomicInteger callCount = new AtomicInteger();
        String key = "key";

        cache.getOrLoad(key, () -> {
            callCount.incrementAndGet();
            return new KisTokenEntry("token", TTL);
        });

        cache.evict(key);
        assertThat(cache.peek(key)).isEmpty();

        cache.getOrLoad(key, () -> {
            callCount.incrementAndGet();
            return new KisTokenEntry("token", TTL);
        });

        assertThat(callCount.get()).isEqualTo(2);
    }

    // Caffeine의 Ticker는 read()가 나노초 시각을 반환해야 한다 - 실제 시간 대신 테스트가
    // 직접 흐르게 만들 수 있는 가짜 시계로, Thread.sleep 없이 TTL 만료를 재현하기 위해 쓴다.
    private static final class FakeTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }
}
