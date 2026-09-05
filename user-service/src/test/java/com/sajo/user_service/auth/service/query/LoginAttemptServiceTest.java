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
 
import static org.assertj.core.api.Assertions.assertThat;
 
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
}
