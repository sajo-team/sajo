package com.sajo.market_service.strategy.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis 컨테이너에 대고 claim/complete/release/clearIfNotProcessing의 상태 전이를 검증한다.
 * 대부분의 시나리오는 스레드 경쟁 없이 순서대로 직접 호출해 "여러 요청이 이 순서로 끼어들었다면"을
 * 결정적으로 재현한다(LoginAttemptServiceTest와 동일하게 Spring 컨텍스트 없이 StringRedisTemplate만 구성).
 * 순수 동시성(같은 순간 경합)만 실제 스레드로 검증한다.
 */
@Testcontainers
@EnabledIfDockerAvailable
@DisplayName("SignalStateStore 실제 Redis 컨테이너 통합 테스트")
class SignalStateStoreTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    private StringRedisTemplate redisTemplate() {
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    private String uniqueKey() {
        return "test:signal-state:" + UUID.randomUUID();
    }

    @Test
    @DisplayName("키가 없으면 선점에 성공한다")
    void claimSucceedsWhenKeyAbsent() {
        StringRedisTemplate redisTemplate = redisTemplate();
        SignalStateStore store = new SignalStateStore(redisTemplate);
        String key = uniqueKey();

        boolean claimed = store.claim(key, "token-a", "BUY", Duration.ofSeconds(30));

        assertThat(claimed).isTrue();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("PROCESSING:token-a");
    }

    @Test
    @DisplayName("이미 같은 방향으로 완료된 상태면 선점에 실패한다(동일 방향 중복 방지)")
    void claimFailsWhenAlreadyCompletedWithSameDirection() {
        StringRedisTemplate redisTemplate = redisTemplate();
        SignalStateStore store = new SignalStateStore(redisTemplate);
        String key = uniqueKey();
        redisTemplate.opsForValue().set(key, "BUY");

        boolean claimed = store.claim(key, "token-b", "BUY", Duration.ofSeconds(30));

        assertThat(claimed).isFalse();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("BUY"); // 건드리지 않음
    }

    @Test
    @DisplayName("반대 방향이 이미 완료된 상태면 새로 선점할 수 있다(구간 전환)")
    void claimSucceedsWhenOppositeDirectionAlreadyCompleted() {
        StringRedisTemplate redisTemplate = redisTemplate();
        SignalStateStore store = new SignalStateStore(redisTemplate);
        String key = uniqueKey();
        redisTemplate.opsForValue().set(key, "BUY");

        boolean claimed = store.claim(key, "token-c", "SELL", Duration.ofSeconds(30));

        assertThat(claimed).isTrue();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("PROCESSING:token-c");
    }

    @Test
    @DisplayName("다른 요청이 처리 중이면(PROCESSING) 방향이 달라도 선점에 실패한다(BUY-SELL 동시 요청)")
    void claimFailsWhileAnotherRequestIsProcessingRegardlessOfDirection() {
        StringRedisTemplate redisTemplate = redisTemplate();
        SignalStateStore store = new SignalStateStore(redisTemplate);
        String key = uniqueKey();
        // 요청 A(BUY)가 먼저 선점을 끝낸 상태를 재현한다.
        assertThat(store.claim(key, "token-a", "BUY", Duration.ofSeconds(30))).isTrue();

        // 요청 B(SELL)가 뒤이어 같은 키를 선점 시도한다.
        boolean claimedByB = store.claim(key, "token-b", "SELL", Duration.ofSeconds(30));

        assertThat(claimedByB).isFalse();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("PROCESSING:token-a"); // A의 선점 유지
    }

    @Test
    @DisplayName("내 토큰일 때만 완료 상태로 전환된다")
    void completeSucceedsOnlyWithOwnToken() {
        StringRedisTemplate redisTemplate = redisTemplate();
        SignalStateStore store = new SignalStateStore(redisTemplate);
        String key = uniqueKey();
        store.claim(key, "token-a", "BUY", Duration.ofSeconds(30));

        boolean completedByWrongToken = store.complete(key, "token-b", "BUY", Duration.ofDays(7));
        boolean completedByOwner = store.complete(key, "token-a", "BUY", Duration.ofDays(7));

        assertThat(completedByWrongToken).isFalse();
        assertThat(completedByOwner).isTrue();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("BUY");
    }

    @Test
    @DisplayName("다른 요청이 이미 재선점·완료한 뒤에는 이전 요청의 release가 그 상태를 지우지 않는다")
    void releaseDoesNotClobberStateClaimedByAnotherRequestAfterFailure() {
        StringRedisTemplate redisTemplate = redisTemplate();
        SignalStateStore store = new SignalStateStore(redisTemplate);
        String key = uniqueKey();

        // 1) 요청 A가 선점한다.
        store.claim(key, "token-a", "BUY", Duration.ofSeconds(30));
        // 2) A의 PROCESSING이 TTL 만료 등으로 사라진 뒤, 요청 B가 같은 키를 재선점하고 완료까지 마쳤다고
        //    가정한다(TTL 만료는 키를 직접 지워 흉내낸다).
        redisTemplate.delete(key);
        store.claim(key, "token-b", "BUY", Duration.ofSeconds(30));
        store.complete(key, "token-b", "BUY", Duration.ofDays(7));

        // 3) 뒤늦게 요청 A가 자신의(이제는 무효한) 토큰으로 release를 시도한다.
        store.release(key, "token-a");

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("BUY"); // B가 완료한 상태가 유지됨
    }

    @Test
    @DisplayName("내 토큰일 때만 release로 삭제된다")
    void releaseDeletesOnlyWithOwnToken() {
        StringRedisTemplate redisTemplate = redisTemplate();
        SignalStateStore store = new SignalStateStore(redisTemplate);
        String key = uniqueKey();
        store.claim(key, "token-a", "BUY", Duration.ofSeconds(30));

        store.release(key, "token-wrong");
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("PROCESSING:token-a"); // 삭제 안 됨

        store.release(key, "token-a");
        assertThat(redisTemplate.opsForValue().get(key)).isNull(); // 삭제됨
    }

    @Test
    @DisplayName("PROCESSING 중에는 clearIfNotProcessing이 상태를 지우지 않는다(조건 미충족과 발행 동시 실행)")
    void clearIfNotProcessingDoesNotTouchInFlightClaim() {
        StringRedisTemplate redisTemplate = redisTemplate();
        SignalStateStore store = new SignalStateStore(redisTemplate);
        String key = uniqueKey();
        store.claim(key, "token-a", "BUY", Duration.ofSeconds(30));

        store.clearIfNotProcessing(key);

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("PROCESSING:token-a");
    }

    @Test
    @DisplayName("완료 상태에서는 clearIfNotProcessing이 정상적으로 삭제한다")
    void clearIfNotProcessingDeletesCompletedState() {
        StringRedisTemplate redisTemplate = redisTemplate();
        SignalStateStore store = new SignalStateStore(redisTemplate);
        String key = uniqueKey();
        redisTemplate.opsForValue().set(key, "BUY");

        store.clearIfNotProcessing(key);

        assertThat(redisTemplate.opsForValue().get(key)).isNull();
    }

    @Test
    @DisplayName("PROCESSING TTL이 만료되면 재선점(재시도)할 수 있다")
    void claimSucceedsAgainAfterProcessingTtlExpires() throws InterruptedException {
        StringRedisTemplate redisTemplate = redisTemplate();
        SignalStateStore store = new SignalStateStore(redisTemplate);
        String key = uniqueKey();
        store.claim(key, "token-a", "BUY", Duration.ofSeconds(1));

        TimeUnit.MILLISECONDS.sleep(1_200);

        boolean claimedAfterExpiry = store.claim(key, "token-b", "BUY", Duration.ofSeconds(30));

        assertThat(claimedAfterExpiry).isTrue();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("PROCESSING:token-b");
    }

    @Test
    @DisplayName("같은 방향 BUY 동시 요청 20건 중 정확히 하나만 선점에 성공한다")
    void exactlyOneClaimSucceedsUnderConcurrentSameDirectionRequests() throws Exception {
        StringRedisTemplate redisTemplate = redisTemplate();
        SignalStateStore store = new SignalStateStore(redisTemplate);
        String key = uniqueKey();
        int concurrency = 20;

        ExecutorService executorService = Executors.newFixedThreadPool(concurrency);
        CountDownLatch readyLatch = new CountDownLatch(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();

        try {
            List<Future<?>> futures = IntStream.range(0, concurrency)
                    .mapToObj(i -> executorService.submit(() -> {
                        readyLatch.countDown();
                        awaitUninterruptibly(startLatch);
                        if (store.claim(key, "token-" + i, "BUY", Duration.ofSeconds(30))) {
                            successCount.incrementAndGet();
                        }
                    }))
                    .collect(Collectors.toList());

            readyLatch.await();
            startLatch.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executorService.shutdownNow();
        }

        assertThat(successCount.get()).isEqualTo(1);
    }

    private void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
