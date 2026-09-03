package com.sajo.user_service.account.service.command;

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
        KisTokenCacheCommandService.class, KisTokenCacheCommandServiceCacheTest.CacheTestConfig.class})
class KisTokenCacheCommandServiceCacheTest {

    @Autowired
    private KisTokenCacheCommandService kisTokenCacheCommandService;

    @Autowired
    private CacheManager cacheManager;

    @Test
    @DisplayName("primeKisAccessTokenCache로 채워두면 kis-access-token 캐시에 해당 userId로 값이 저장된다")
    void primeKisAccessTokenCacheStoresValueInCache() {
        // given
        UUID userId = UUID.randomUUID();
        String primed = "primed-token";

        // when
        String result = kisTokenCacheCommandService.primeKisAccessTokenCache(userId, primed);

        // then
        assertThat(result).isEqualTo(primed);
        assertThat(cacheManager.getCache("kis-access-token").get(userId, String.class))
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
