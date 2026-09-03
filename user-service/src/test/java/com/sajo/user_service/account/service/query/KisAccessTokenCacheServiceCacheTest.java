package com.sajo.user_service.account.service.query;

import com.sajo.user_service.account.client.KisClient;
import com.sajo.user_service.account.client.dto.response.KisAccessTokenResponse;
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
        KisAccessTokenCacheService.class, KisAccessTokenCacheServiceCacheTest.CacheTestConfig.class})
class KisAccessTokenCacheServiceCacheTest {

    @Autowired
    private KisAccessTokenCacheService kisAccessTokenCacheService;

    @MockitoBean
    private KisClient kisClient;

    @Test
    @DisplayName("같은 userId로 두 번 호출하면 KIS는 한 번만 호출되고 캐시된 accessToken을 그대로 반환한다")
    void getAccessTokenIsCachedPerUserId() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400f, "2026-01-01 00:00:00"));

        // when
        String first = kisAccessTokenCacheService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL);
        String second = kisAccessTokenCacheService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(second).isEqualTo(first);
        verify(kisClient, times(1)).getAccessToken("app-key", "secret-key", AccountType.REAL);
    }

    @Configuration
    @EnableCaching
    static class CacheTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("kis-access-token");
        }
    }
}
