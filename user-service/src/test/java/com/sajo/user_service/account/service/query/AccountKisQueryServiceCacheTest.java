package com.sajo.user_service.account.service.query;

import com.sajo.user_service.account.cache.KisTokenCacheLock;
import com.sajo.user_service.account.client.kis.KisOAuthClient;
import com.sajo.user_service.account.client.kis.KisTrClient;
import com.sajo.user_service.account.client.kis.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.client.kis.dto.response.KisApprovalKeyResponse;
import com.sajo.user_service.account.controller.dto.response.AccessTokenResponse;
import com.sajo.user_service.account.controller.dto.response.ApprovalKeyResponse;
import com.sajo.user_service.account.domain.Account;
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
// 전환하면서 진짜 Redis로 검증한다.
@Testcontainers
@EnabledIfDockerAvailable
@SpringBootTest(classes = {
        AccountKisQueryService.class, KisTokenCacheQueryService.class, KisTokenCacheLock.class,
        DataRedisAutoConfiguration.class})
@DisplayName("계좌 KIS 조회 - 실제 Redis 캐시 통합 테스트")
class AccountKisQueryServiceCacheTest {

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private AccountKisQueryService accountKisQueryService;

    @MockitoBean
    private AccountQueryService accountQueryService;

    @MockitoBean
    private KisOAuthClient kisOAuthClient;

    @MockitoBean
    private KisTrClient kisTrClient;

    @MockitoBean
    private KisTokenLogCommandService kisTokenLogCommandService;

    @Test
    @DisplayName("같은 userId로 두 번 호출하면 KIS 호출은 캐시로 한 번만 일어나지만, "
            + "appKey/secretKey는 캐시에 두지 않으므로 계좌 조회는 매번 일어난다")
    void getKisAccessTokenCachesOnlyKisCallNotAccountLookup() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        KisAccessTokenResponse kisResponse =
                new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00");

        given(accountQueryService.getAccountByUserId(userId)).willReturn(account);
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL)).willReturn(kisResponse);

        // when
        AccessTokenResponse first = accountKisQueryService.getKisAccessToken(userId);
        AccessTokenResponse second = accountKisQueryService.getKisAccessToken(userId);

        // then
        assertThat(second).isEqualTo(first);
        verify(kisOAuthClient, times(1)).getAccessToken("app-key", "secret-key", AccountType.REAL);
        verify(accountQueryService, times(2)).getAccountByUserId(userId);
    }

    @Test
    @DisplayName("userId가 다르면 캐시를 공유하지 않고 각각 KIS를 호출한다")
    void getKisAccessTokenIsCachedPerDistinctUserId() {
        // given
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        Account account1 = Account.createAccount(
                userId1, "app-key-1", "secret-key-1", "11111111-11", "hashed-account-no-1", AccountType.REAL);
        Account account2 = Account.createAccount(
                userId2, "app-key-2", "secret-key-2", "22222222-22", "hashed-account-no-2", AccountType.REAL);

        given(accountQueryService.getAccountByUserId(userId1)).willReturn(account1);
        given(accountQueryService.getAccountByUserId(userId2)).willReturn(account2);
        given(kisOAuthClient.getAccessToken("app-key-1", "secret-key-1", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("token-1", "Bearer", 86400, "2026-01-01 00:00:00"));
        given(kisOAuthClient.getAccessToken("app-key-2", "secret-key-2", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("token-2", "Bearer", 86400, "2026-01-01 00:00:00"));

        // when
        AccessTokenResponse result1 = accountKisQueryService.getKisAccessToken(userId1);
        AccessTokenResponse result2 = accountKisQueryService.getKisAccessToken(userId2);

        // then
        assertThat(result1.accessToken()).isEqualTo("token-1");
        assertThat(result2.accessToken()).isEqualTo("token-2");
        verify(kisOAuthClient, times(1)).getAccessToken("app-key-1", "secret-key-1", AccountType.REAL);
        verify(kisOAuthClient, times(1)).getAccessToken("app-key-2", "secret-key-2", AccountType.REAL);
    }

    @Test
    @DisplayName("같은 userId로 접속키를 두 번 조회하면 KIS 호출은 캐시로 한 번만 일어난다")
    void getKisApprovalKeyIsCachedPerUserId() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        KisApprovalKeyResponse kisResponse = new KisApprovalKeyResponse("issued-approval-key");

        given(accountQueryService.getAccountByUserId(userId)).willReturn(account);
        given(kisOAuthClient.getApprovalKey("app-key", "secret-key", AccountType.REAL)).willReturn(kisResponse);

        // when
        ApprovalKeyResponse first = accountKisQueryService.getKisApprovalKey(userId);
        ApprovalKeyResponse second = accountKisQueryService.getKisApprovalKey(userId);

        // then
        assertThat(second).isEqualTo(first);
        verify(kisOAuthClient, times(1)).getApprovalKey("app-key", "secret-key", AccountType.REAL);
        verify(accountQueryService, times(2)).getAccountByUserId(userId);
    }

    @Test
    @DisplayName("접근토큰 캐시와 접속키 캐시는 서로 섞이지 않는다")
    void accessTokenAndApprovalKeyCachesAreIndependent() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);

        given(accountQueryService.getAccountByUserId(userId)).willReturn(account);
        given(kisOAuthClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400, "2026-01-01 00:00:00"));
        given(kisOAuthClient.getApprovalKey("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisApprovalKeyResponse("issued-approval-key"));

        // when
        AccessTokenResponse accessToken = accountKisQueryService.getKisAccessToken(userId);
        ApprovalKeyResponse approvalKey = accountKisQueryService.getKisApprovalKey(userId);

        // then
        assertThat(accessToken.accessToken()).isEqualTo("issued-token");
        assertThat(approvalKey.approvalKey()).isEqualTo("issued-approval-key");
        verify(kisOAuthClient, times(1)).getAccessToken("app-key", "secret-key", AccountType.REAL);
        verify(kisOAuthClient, times(1)).getApprovalKey("app-key", "secret-key", AccountType.REAL);
    }
}
