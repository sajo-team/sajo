package com.sajo.user_service.account.service.command;

import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
        AccountKisCommandService.class, AccountKisCommandServiceCacheTest.CacheTestConfig.class})
class AccountKisCommandServiceCacheTest {

    @Autowired
    private AccountKisCommandService accountKisCommandService;

    @Autowired
    private CacheManager cacheManager;

    @Test
    @DisplayName("primeKisAccessTokenCache로 채워두면 kis-access-token 캐시에 해당 userId로 값이 저장된다")
    void primeKisAccessTokenCacheStoresValueInCache() {
        // given
        UUID userId = UUID.randomUUID();
        AccessTokenResponse primed = new AccessTokenResponse("primed-token", "app-key", "secret-key");

        // when
        AccessTokenResponse result = accountKisCommandService.primeKisAccessTokenCache(userId, primed);

        // then
        assertThat(result).isEqualTo(primed);
        assertThat(cacheManager.getCache("kis-access-token").get(userId, AccessTokenResponse.class))
                .isEqualTo(primed);
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
