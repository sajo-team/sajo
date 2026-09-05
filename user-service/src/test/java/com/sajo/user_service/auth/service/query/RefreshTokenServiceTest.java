package com.sajo.user_service.auth.service.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 Redis 컨테이너에 대고 발급/회전/재사용 탐지/무효화 동작을 검증한다.
@Testcontainers
@EnabledIfDockerAvailable
@DisplayName("RefreshTokenService 실제 Redis 컨테이너 통합 테스트")
class RefreshTokenServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate(connectionFactory);
        stringRedisTemplate.afterPropertiesSet();

        refreshTokenService = new RefreshTokenService(stringRedisTemplate);
    }

    @Test
    @DisplayName("발급한 토큰으로 곧바로 회전하면 새 토큰과 동일한 userId를 반환한다")
    void issueThenRotateSucceeds() {
        // given
        UUID userId = UUID.randomUUID();
        String token = refreshTokenService.issue(userId).orElseThrow();

        // when
        Optional<RefreshTokenService.RotationResult> result = refreshTokenService.rotate(token);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo(userId);
        assertThat(result.get().newRefreshToken()).isNotEqualTo(token);
    }

    @Test
    @DisplayName("존재하지 않는 토큰으로 회전을 시도하면 빈 Optional을 반환한다")
    void rotateFailsForUnknownToken() {
        // when
        Optional<RefreshTokenService.RotationResult> result = refreshTokenService.rotate("no-such-token");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("회전 후 옛 토큰으로 다시 회전을 시도하면(재사용) 실패하고, 새로 회전된 토큰도 함께 무효화된다")
    void reusingRotatedOutTokenInvalidatesEntireSession() {
        // given
        UUID userId = UUID.randomUUID();
        String originalToken = refreshTokenService.issue(userId).orElseThrow();
        String rotatedToken = refreshTokenService.rotate(originalToken).orElseThrow().newRefreshToken();

        // when - 이미 회전되어 빠진 originalToken을 다시 제시 (탈취 시나리오 재현)
        Optional<RefreshTokenService.RotationResult> reuseAttempt = refreshTokenService.rotate(originalToken);

        // then - 재사용 자체가 거부된다
        assertThat(reuseAttempt).isEmpty();

        // and - 정상적으로 회전되어 있던 rotatedToken(현재 유효했던 토큰)도 함께 무효화된다
        // (탈취 여부를 서버가 구분할 수 없으므로 세션 전체를 무효화하는 정책)
        Optional<RefreshTokenService.RotationResult> afterInvalidation = refreshTokenService.rotate(rotatedToken);
        assertThat(afterInvalidation).isEmpty();
    }

    @Test
    @DisplayName("로그아웃(revoke) 이후에는 발급했던 토큰으로 회전할 수 없다")
    void revokeInvalidatesCurrentToken() {
        // given
        UUID userId = UUID.randomUUID();
        String token = refreshTokenService.issue(userId).orElseThrow();

        // when
        refreshTokenService.revoke(userId);

        // then
        assertThat(refreshTokenService.rotate(token)).isEmpty();
    }

    @Test
    @DisplayName("서로 다른 사용자의 토큰은 독립적으로 동작한다")
    void tokensAreIndependentPerUser() {
        // given
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String tokenA = refreshTokenService.issue(userA).orElseThrow();
        String tokenB = refreshTokenService.issue(userB).orElseThrow();

        // when
        refreshTokenService.revoke(userA);

        // then - A는 무효화됐지만 B는 영향 없어야 한다
        assertThat(refreshTokenService.rotate(tokenA)).isEmpty();
        assertThat(refreshTokenService.rotate(tokenB)).isPresent();
    }

    // 리뷰 반영 - 같은 토큰으로 거의 동시에 두 요청이 rotate()를 시도하면(네트워크 재시도,
    // 다중 탭 등), 원자성이 없었다면 둘 다 검증을 통과해 정상 사용자의 세션이 재사용
    // 오탐으로 무효화될 수 있었다. 실제로 여러 스레드를 동시에 출발시켜, 정확히 하나만
    // 성공하고 나머지는 전부 실패하는지(=이미 회전된 토큰으로 처리되는지) 검증한다.
    @Test
    @DisplayName("같은 토큰으로 동시에 여러 요청이 회전을 시도해도 정확히 하나만 성공한다")
    void concurrentRotateWithSameTokenOnlyOneSucceeds() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        String token = refreshTokenService.issue(userId).orElseThrow();
        int threadCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Future<Optional<RefreshTokenService.RotationResult>>> futures = new ArrayList<>();

        // when - 모든 스레드가 barrier에서 동시에 출발하도록 맞춰서 rotate()를 호출한다
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                return refreshTokenService.rotate(token);
            }));
        }

        long successCount = 0;
        for (Future<Optional<RefreshTokenService.RotationResult>> future : futures) {
            if (future.get().isPresent()) {
                successCount++;
            }
        }
        executor.shutdown();

        // then - 원자성이 보장되므로 정확히 하나만 성공해야 한다
        assertThat(successCount).isEqualTo(1);
    }
}
