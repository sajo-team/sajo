package com.other.redis;

import com.other.TestApplication;
import com.sajo.other.redis.TestValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@EnabledIfDockerAvailable
@SpringBootTest(classes = TestApplication.class)
@DisplayName("Redis 실제 컨테이너 통합 테스트")
class RedisIntegrationTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CountingService countingService;

    @Test
    @DisplayName("RedisTemplate으로 저장한 값을 원래 타입 그대로 다시 읽어온다")
    void redisTemplateRoundTripsAJsonValue() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        redisTemplate.opsForValue().set("test:item", new TestValue("hello", 42, now));

        Object result = redisTemplate.opsForValue().get("test:item");

        assertThat(result).isEqualTo(new TestValue("hello", 42, now));
    }

    @Test
    @DisplayName("같은 key로 두 번 호출하면 두 번째는 캐시에서 히트하고 메서드는 한 번만 실행된다")
    void cacheableMethodIsOnlyInvokedOnceForTheSameKey() throws InterruptedException {
        countingService.reset();

        TestValue first = countingService.getValue("k1");

        // Testcontainers Redis에 대한 Lettuce 공유 커넥션이 막 맺어진 직후라
        // 바로 이어지는 GET이 방금 SET한 값을 못 볼 수 있어서(재연결 레이스) 살짝 텀을 둔다.
        Thread.sleep(150);

        TestValue second = countingService.getValue("k1");

        assertThat(first).isEqualTo(second);
        assertThat(countingService.getInvocationCount()).isEqualTo(1);
    }
}
