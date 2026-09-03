package com.sajo.user_service.account.service.query;

import com.sajo.user_service.account.client.KisClient;
import com.sajo.user_service.account.client.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.client.dto.response.KisApprovalKeyResponse;
import com.sajo.user_service.account.domain.AccountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = {
        KisTokenCacheQueryService.class, KisTokenCacheQueryServiceCacheTest.CacheTestConfig.class})
class KisTokenCacheQueryServiceCacheTest {

    @Autowired
    private KisTokenCacheQueryService kisTokenCacheQueryService;

    @MockitoBean
    private KisClient kisClient;

    @Test
    @DisplayName("같은 userId로 접근토큰을 두 번 조회하면 KIS는 한 번만 호출되고 캐시된 값을 그대로 반환한다")
    void getAccessTokenIsCachedPerUserId() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400f, "2026-01-01 00:00:00"));

        // when
        String first = kisTokenCacheQueryService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL);
        String second = kisTokenCacheQueryService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(second).isEqualTo(first);
        verify(kisClient, times(1)).getAccessToken("app-key", "secret-key", AccountType.REAL);
    }

    @Test
    @DisplayName("같은 userId로 접속키를 두 번 조회하면 KIS는 한 번만 호출되고 캐시된 값을 그대로 반환한다")
    void getApprovalKeyIsCachedPerUserId() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisClient.getApprovalKey("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisApprovalKeyResponse("issued-approval-key"));

        // when
        String first = kisTokenCacheQueryService.getApprovalKey(userId, "app-key", "secret-key", AccountType.REAL);
        String second = kisTokenCacheQueryService.getApprovalKey(userId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(second).isEqualTo(first);
        verify(kisClient, times(1)).getApprovalKey("app-key", "secret-key", AccountType.REAL);
    }

    @Test
    @DisplayName("접근토큰 캐시와 접속키 캐시는 서로 섞이지 않는다")
    void accessTokenAndApprovalKeyCachesAreIndependent() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400f, "2026-01-01 00:00:00"));
        given(kisClient.getApprovalKey("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisApprovalKeyResponse("issued-approval-key"));

        // when
        String accessToken = kisTokenCacheQueryService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL);
        String approvalKey = kisTokenCacheQueryService.getApprovalKey(userId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(accessToken).isEqualTo("issued-token");
        assertThat(approvalKey).isEqualTo("issued-approval-key");
        verify(kisClient, times(1)).getAccessToken("app-key", "secret-key", AccountType.REAL);
        verify(kisClient, times(1)).getApprovalKey("app-key", "secret-key", AccountType.REAL);
    }

    @Configuration
    @EnableCaching
    static class CacheTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("kis-access-token", "kis-approval-key");
        }
    }
}
