package com.sajo.user_service.account.service.query;

import com.sajo.user_service.account.client.KisClient;
import com.sajo.user_service.account.client.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.client.dto.response.KisApprovalKeyResponse;
import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
import com.sajo.user_service.account.controller.dto.response.ApprovalKeyResponse;
import com.sajo.user_service.account.domain.Account;
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
        AccountKisQueryService.class, KisTokenCacheService.class, AccountKisQueryServiceCacheTest.CacheTestConfig.class})
class AccountKisQueryServiceCacheTest {

    @Autowired
    private AccountKisQueryService accountKisQueryService;

    @MockitoBean
    private AccountQueryService accountQueryService;

    @MockitoBean
    private KisClient kisClient;

    @Test
    @DisplayName("같은 userId로 두 번 호출하면 KIS 호출은 캐시로 한 번만 일어나지만, "
            + "appKey/secretKey는 캐시에 두지 않으므로 계좌 조회는 매번 일어난다")
    void getKisAccessTokenCachesOnlyKisCallNotAccountLookup() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "123-456-789", "hashed-account-no", AccountType.REAL);
        KisAccessTokenResponse kisResponse =
                new KisAccessTokenResponse("issued-token", "Bearer", 86400f, "2026-01-01 00:00:00");

        given(accountQueryService.getAccountByUserId(userId)).willReturn(account);
        given(kisClient.getAccessToken("app-key", "secret-key", AccountType.REAL)).willReturn(kisResponse);

        // when
        AccessTokenResponse first = accountKisQueryService.getKisAccessToken(userId);
        AccessTokenResponse second = accountKisQueryService.getKisAccessToken(userId);

        // then
        assertThat(second).isEqualTo(first);
        verify(kisClient, times(1)).getAccessToken("app-key", "secret-key", AccountType.REAL);
        verify(accountQueryService, times(2)).getAccountByUserId(userId);
    }

    @Test
    @DisplayName("userId가 다르면 캐시를 공유하지 않고 각각 KIS를 호출한다")
    void getKisAccessTokenIsCachedPerDistinctUserId() {
        // given
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        Account account1 = Account.createAccount(
                userId1, "app-key-1", "secret-key-1", "111-111-111", "hashed-account-no-1", AccountType.REAL);
        Account account2 = Account.createAccount(
                userId2, "app-key-2", "secret-key-2", "222-222-222", "hashed-account-no-2", AccountType.REAL);

        given(accountQueryService.getAccountByUserId(userId1)).willReturn(account1);
        given(accountQueryService.getAccountByUserId(userId2)).willReturn(account2);
        given(kisClient.getAccessToken("app-key-1", "secret-key-1", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("token-1", "Bearer", 86400f, "2026-01-01 00:00:00"));
        given(kisClient.getAccessToken("app-key-2", "secret-key-2", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("token-2", "Bearer", 86400f, "2026-01-01 00:00:00"));

        // when
        AccessTokenResponse result1 = accountKisQueryService.getKisAccessToken(userId1);
        AccessTokenResponse result2 = accountKisQueryService.getKisAccessToken(userId2);

        // then
        assertThat(result1.accessToken()).isEqualTo("token-1");
        assertThat(result2.accessToken()).isEqualTo("token-2");
        verify(kisClient, times(1)).getAccessToken("app-key-1", "secret-key-1", AccountType.REAL);
        verify(kisClient, times(1)).getAccessToken("app-key-2", "secret-key-2", AccountType.REAL);
    }

    @Test
    @DisplayName("같은 userId로 접속키를 두 번 조회하면 KIS 호출은 캐시로 한 번만 일어난다")
    void getKisApprovalKeyIsCachedPerUserId() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "123-456-789", "hashed-account-no", AccountType.REAL);
        KisApprovalKeyResponse kisResponse = new KisApprovalKeyResponse("issued-approval-key");

        given(accountQueryService.getAccountByUserId(userId)).willReturn(account);
        given(kisClient.getApprovalKey("app-key", "secret-key", AccountType.REAL)).willReturn(kisResponse);

        // when
        ApprovalKeyResponse first = accountKisQueryService.getKisApprovalKey(userId);
        ApprovalKeyResponse second = accountKisQueryService.getKisApprovalKey(userId);

        // then
        assertThat(second).isEqualTo(first);
        verify(kisClient, times(1)).getApprovalKey("app-key", "secret-key", AccountType.REAL);
        verify(accountQueryService, times(2)).getAccountByUserId(userId);
    }

    @Test
    @DisplayName("접근토큰 캐시와 접속키 캐시는 서로 섞이지 않는다")
    void accessTokenAndApprovalKeyCachesAreIndependent() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "123-456-789", "hashed-account-no", AccountType.REAL);

        given(accountQueryService.getAccountByUserId(userId)).willReturn(account);
        given(kisClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400f, "2026-01-01 00:00:00"));
        given(kisClient.getApprovalKey("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisApprovalKeyResponse("issued-approval-key"));

        // when
        AccessTokenResponse accessToken = accountKisQueryService.getKisAccessToken(userId);
        ApprovalKeyResponse approvalKey = accountKisQueryService.getKisApprovalKey(userId);

        // then
        assertThat(accessToken.accessToken()).isEqualTo("issued-token");
        assertThat(approvalKey.approvalKey()).isEqualTo("issued-approval-key");
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
