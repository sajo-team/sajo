package com.sajo.user_service.account.service.query;

import com.sajo.user_service.account.cache.KisTokenCacheLock;
import com.sajo.user_service.account.client.kis.KisOAuthClient;
import com.sajo.user_service.account.client.kis.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.client.kis.dto.response.KisApprovalKeyResponse;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.service.command.KisTokenLogCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers
@EnabledIfDockerAvailable
@SpringBootTest(classes = {
        KisTokenCacheQueryService.class, KisTokenCacheLock.class, DataRedisAutoConfiguration.class})
@DisplayName("KIS 토큰 캐시 - 실제 Redis 통합 테스트")
class KisTokenCacheQueryServiceCacheTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private KisTokenCacheQueryService kisTokenCacheQueryService;

    @MockitoBean
    private KisOAuthClient kisOAuthClient;

    @MockitoBean
    private KisTokenLogCommandService kisTokenLogCommandService;

    @Test
    @DisplayName("같은 userId로 접근토큰을 두 번 조회하면 KIS는 한 번만 호출되고 캐시된 값을 그대로 반환한다")
    void getAccessTokenIsCachedPerUserId() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00"));

        // when
        String first = kisTokenCacheQueryService.getAccessToken(userId, null, "app-key", "secret-key", AccountType.REAL);
        String second = kisTokenCacheQueryService.getAccessToken(userId, null, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(second).isEqualTo(first);
        verify(kisOAuthClient, times(1)).getAccessToken("app-key", "secret-key", AccountType.REAL);
    }

    @Test
    @DisplayName("같은 userId로 접속키를 두 번 조회하면 KIS는 한 번만 호출되고 캐시된 값을 그대로 반환한다")
    void getApprovalKeyIsCachedPerUserId() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisOAuthClient.getApprovalKey("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisApprovalKeyResponse("issued-approval-key"));

        // when
        String first = kisTokenCacheQueryService.getApprovalKey(userId, null, "app-key", "secret-key", AccountType.REAL);
        String second = kisTokenCacheQueryService.getApprovalKey(userId, null, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(second).isEqualTo(first);
        verify(kisOAuthClient, times(1)).getApprovalKey("app-key", "secret-key", AccountType.REAL);
    }

    @Test
    @DisplayName("접근토큰 캐시와 접속키 캐시는 서로 섞이지 않는다")
    void accessTokenAndApprovalKeyCachesAreIndependent() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00"));
        given(kisOAuthClient.getApprovalKey("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisApprovalKeyResponse("issued-approval-key"));

        // when
        String accessToken = kisTokenCacheQueryService.getAccessToken(userId, null, "app-key", "secret-key", AccountType.REAL);
        String approvalKey = kisTokenCacheQueryService.getApprovalKey(userId, null, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(accessToken).isEqualTo("issued-token");
        assertThat(approvalKey).isEqualTo("issued-approval-key");
        verify(kisOAuthClient, times(1)).getAccessToken("app-key", "secret-key", AccountType.REAL);
        verify(kisOAuthClient, times(1)).getApprovalKey("app-key", "secret-key", AccountType.REAL);
    }

    @Test
    @DisplayName("같은 userId에 대해 동시에 여러 요청이 몰려도 캐시 미스 시 KIS는 딱 한 번만 호출된다")
    void getAccessToken_concurrentRequestsForSameUser_callsKisOnlyOnce() throws InterruptedException {
        // given - KIS 호출이 순간적으로 끝나지 않게 살짝 지연을 줘서, 나머지 스레드들이 실제로
        // 락 대기(재확인 폴링) 경로를 타도록 만든다
        UUID userId = UUID.randomUUID();
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willAnswer(invocation -> {
                    Thread.sleep(200);
                    return new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00");
                });

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        // when - 모든 스레드가 준비될 때까지 기다렸다가 한 번에 출발시켜서 동시성을 최대한 끌어올린다
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    results.add(kisTokenCacheQueryService.getAccessToken(
                            userId, null, "app-key", "secret-key", AccountType.REAL));
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        readyLatch.await();
        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(completed).as("모든 스레드가 시간 안에 끝나야 함").isTrue();
        assertThat(failures).isEmpty();
        assertThat(results).hasSize(threadCount);
        assertThat(results).allMatch("issued-token"::equals);
        verify(kisOAuthClient, times(1)).getAccessToken("app-key", "secret-key", AccountType.REAL);
    }
}
