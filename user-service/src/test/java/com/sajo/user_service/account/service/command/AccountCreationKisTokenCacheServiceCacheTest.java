package com.sajo.user_service.account.service.command;

import com.sajo.common.redis.config.CommonRedisAutoConfiguration;
import com.sajo.user_service.account.client.kis.KisOAuthClient;
import com.sajo.user_service.account.client.kis.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.domain.AccountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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

// 계좌 생성 중 accountNo만 틀려 재시도할 때 KIS 접근토큰 발급(1분당 1회 제한)에 다시 걸리지 않도록
// 캐싱한 게 실제로 동작하는지, 그리고 appKey/secretKey가 바뀌면 캐시를 공유하지 않는지(재시도 시 자격증명
// 불일치 문제 방지) 실제 Redis로 검증한다.
@Testcontainers
@EnabledIfDockerAvailable
@SpringBootTest(classes = {
        AccountCreationKisTokenCacheService.class, CommonRedisAutoConfiguration.class,
        DataRedisAutoConfiguration.class, JacksonAutoConfiguration.class})
@DisplayName("계좌 생성용 KIS 토큰 캐시 - 실제 Redis 캐시 통합 테스트")
class AccountCreationKisTokenCacheServiceCacheTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private AccountCreationKisTokenCacheService accountCreationKisTokenCacheService;

    @MockitoBean
    private KisOAuthClient kisOAuthClient;

    @Autowired
    private CacheManager cacheManager;

    // @Cacheable의 key SpEL과 반드시 동일한 계산이어야 한다 - 캐시 상태를 직접 확인하기 위한 용도.
    private static String cacheKey(UUID userId, String appKey, String secretKey) {
        return userId + ":" + AccountCreationKisTokenCacheService.hashCredentials(appKey, secretKey);
    }

    // Testcontainers로 갓 띄운 Redis에 대해 Lettuce 커넥션이 자리잡기 전 초반 몇 개 명령에서
    // 실측으로 불안정한 동작이 확인됨(get은 되는데 evict가 반영 안 되는 등) - 매 테스트 시작 전에
    // 미리 명령을 한 번 왕복시켜 커넥션을 데운다.
    @BeforeEach
    void warmUpRedisConnection() {
        Cache cache = cacheManager.getCache("account-creation-token");
        if (cache != null) {
            cache.put("warmup", "warmup");
            cache.evict("warmup");
        }
    }

    @Test
    @DisplayName("같은 userId/appKey/secretKey로 두 번 호출하면 KIS 호출은 한 번만 일어나고 같은 토큰을 반환한다")
    void getAccessTokenCachesPerUserIdAndCredentials() {
        // given
        UUID userId = UUID.randomUUID();
        KisAccessTokenResponse kisResponse =
                new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00");
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL)).willReturn(kisResponse);

        // when
        KisAccessTokenResponse first =
                accountCreationKisTokenCacheService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL);
        KisAccessTokenResponse second =
                accountCreationKisTokenCacheService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(second).isEqualTo(first);
        verify(kisOAuthClient, times(1)).getAccessToken("app-key", "secret-key", AccountType.REAL);
    }

    @Test
    @DisplayName("같은 userId여도 appKey/secretKey가 다르면 캐시를 공유하지 않고 KIS를 다시 호출한다 "
            + "(accountNo 재시도 중 자격증명까지 바뀌는 경우 옛 토큰을 재사용하지 않도록)")
    void getAccessTokenMissesCacheWhenCredentialsDiffer() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("token-1", "Bearer", 86400, "2026-01-01 00:00:00"));
        given(kisOAuthClient.getAccessToken("app-key-2", "secret-key-2", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("token-2", "Bearer", 86400, "2026-01-01 00:00:00"));

        // when
        KisAccessTokenResponse first =
                accountCreationKisTokenCacheService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL);
        KisAccessTokenResponse second = accountCreationKisTokenCacheService.getAccessToken(
                userId, "app-key-2", "secret-key-2", AccountType.REAL);

        // then
        assertThat(first.access_token()).isEqualTo("token-1");
        assertThat(second.access_token()).isEqualTo("token-2");
        verify(kisOAuthClient, times(1)).getAccessToken("app-key", "secret-key", AccountType.REAL);
        verify(kisOAuthClient, times(1)).getAccessToken("app-key-2", "secret-key-2", AccountType.REAL);
    }

    @Test
    @DisplayName("userId가 다르면 같은 appKey/secretKey여도 캐시를 공유하지 않는다")
    void getAccessTokenIsCachedPerDistinctUserId() {
        // given
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00"));

        // when
        accountCreationKisTokenCacheService.getAccessToken(userId1, "app-key", "secret-key", AccountType.REAL);
        accountCreationKisTokenCacheService.getAccessToken(userId2, "app-key", "secret-key", AccountType.REAL);

        // then
        verify(kisOAuthClient, times(2)).getAccessToken("app-key", "secret-key", AccountType.REAL);
    }

    @Test
    @DisplayName("evict 후에는 캐시 항목이 실제로 비어있다 "
            + "(계좌 삭제 시 KIS가 토큰을 폐기하므로, 이 캐시에 남은 같은 토큰도 지워야 함)")
    void evictRemovesCachedToken() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00"));
        accountCreationKisTokenCacheService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL);

        Cache cache = cacheManager.getCache("account-creation-token");
        String key = cacheKey(userId, "app-key", "secret-key");
        assertThat(cache).isNotNull();
        assertThat(cache.get(key)).as("evict 전에는 캐시에 값이 있어야 함").isNotNull();

        // when
        accountCreationKisTokenCacheService.evict(userId, "app-key", "secret-key");

        // then
        assertThat(cache.get(key)).as("evict 후에는 캐시가 비어있어야 함").isNull();
    }

    @Test
    @DisplayName("evict 후 다시 조회하면 캐시 미스로 KIS를 다시 호출한다")
    void evictedTokenCausesFreshKisCallOnNextFetch() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00"));
        accountCreationKisTokenCacheService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL);
        accountCreationKisTokenCacheService.evict(userId, "app-key", "secret-key");

        // when
        accountCreationKisTokenCacheService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL);

        // then
        verify(kisOAuthClient, times(2)).getAccessToken("app-key", "secret-key", AccountType.REAL);
    }
}
