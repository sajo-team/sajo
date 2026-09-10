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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 예전엔 @Cacheable + ConcurrentMapCacheManager로 검증했지만, StringRedisTemplate 직접 사용으로
// 전환하면서 실제 캐싱 동작(같은 userId는 KIS를 한 번만 호출)을 검증하려면 진짜 Redis가 필요해졌다.
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
}
