package com.sajo.user_service.auth.service.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// 실제 Redis 컨테이너에 대고 원자적 INCR/EXPIRE 동작을 검증한다. 스프링 컨텍스트 전체를
// 띄우지 않고 StringRedisTemplate만 직접 구성해서 Postgres/Eureka 등과 무관하게 격리시킨다.
// 테스트마다 서로 다른 이메일을 써서 컨테이너를 재사용해도 데이터가 섞이지 않게 한다.
@Testcontainers
@EnabledIfDockerAvailable
@DisplayName("LoginAttemptService 실제 Redis 컨테이너 통합 테스트")
class LoginAttemptServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate(connectionFactory);
        stringRedisTemplate.afterPropertiesSet();

        loginAttemptService = new LoginAttemptService(stringRedisTemplate);
    }

    @Test
    @DisplayName("실패 횟수가 임계치(5회) 미만이면 잠기지 않는다")
    void notLockedBelowThreshold() {
        // given
        String email = "below-threshold@sajo.com";
        for (int i = 0; i < 4; i++) {
            loginAttemptService.recordFailure(email);
        }

        // when & then
        assertThat(loginAttemptService.isLocked(email)).isFalse();
    }

    @Test
    @DisplayName("실패 횟수가 임계치(5회)에 도달하면 잠긴다")
    void lockedAtThreshold() {
        // given
        String email = "at-threshold@sajo.com";
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure(email);
        }

        // when & then
        assertThat(loginAttemptService.isLocked(email)).isTrue();
    }

    @Test
    @DisplayName("성공하면 실패 카운터가 초기화되어 잠금이 풀린다")
    void successResetsCounter() {
        // given
        String email = "reset-on-success@sajo.com";
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure(email);
        }
        assertThat(loginAttemptService.isLocked(email)).isTrue();

        // when
        loginAttemptService.recordSuccess(email);

        // then
        assertThat(loginAttemptService.isLocked(email)).isFalse();
    }

    @Test
    @DisplayName("서로 다른 이메일의 실패 횟수는 독립적으로 카운트된다")
    void countersAreIndependentPerEmail() {
        // given
        String lockedEmail = "independent-a@sajo.com";
        String otherEmail = "independent-b@sajo.com";
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure(lockedEmail);
        }

        // when & then
        assertThat(loginAttemptService.isLocked(lockedEmail)).isTrue();
        assertThat(loginAttemptService.isLocked(otherEmail)).isFalse();
    }

    // 리뷰 반영 - Redis 연결 자체가 안 되는 상황(장애)에서 fail-open이 실제로 동작하는지
    // 검증한다. 정상 컨테이너 대신 아무도 듣지 않는 포트로 연결시켜 실제 연결 실패를
    // 재현한다. 응답을 빨리 받기 위해 커맨드 타임아웃을 짧게 잡는다.
    @Test
    @DisplayName("Redis에 연결할 수 없으면 isLocked는 예외 대신 false를 반환한다 (fail-open)")
    void isLockedFailsOpenWhenRedisUnavailable() {
        // given
        LoginAttemptService brokenService = createServiceWithUnreachableRedis();

        // when & then
        assertThat(brokenService.isLocked("anyone@sajo.com")).isFalse();
    }

    @Test
    @DisplayName("Redis에 연결할 수 없어도 recordFailure/recordSuccess는 예외를 던지지 않는다 (fail-open)")
    void recordMethodsDoNotThrowWhenRedisUnavailable() {
        // given
        LoginAttemptService brokenService = createServiceWithUnreachableRedis();

        // when & then
        assertThatCode(() -> brokenService.recordFailure("anyone@sajo.com")).doesNotThrowAnyException();
        assertThatCode(() -> brokenService.recordSuccess("anyone@sajo.com")).doesNotThrowAnyException();
    }

    private LoginAttemptService createServiceWithUnreachableRedis() {
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(1))
                .build();
        // 포트 1은 권한이 필요한 예약 포트라 테스트 환경에서 아무 프로세스도 듣고 있지 않다 -
        // 실제 Redis 장애(연결 거부)를 안정적으로 재현하기 위한 용도
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 1), clientConfiguration);
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate(connectionFactory);
        stringRedisTemplate.afterPropertiesSet();

        return new LoginAttemptService(stringRedisTemplate);
    }
}
