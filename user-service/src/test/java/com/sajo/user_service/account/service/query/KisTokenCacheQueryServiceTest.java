package com.sajo.user_service.account.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.client.KisClient;
import com.sajo.user_service.account.client.dto.response.KisAccessTokenResponse;
import com.sajo.user_service.account.client.dto.response.KisApprovalKeyResponse;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class KisTokenCacheQueryServiceTest {

    @Mock
    private KisClient kisClient;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private KisTokenCacheQueryService kisTokenCacheQueryService;

    @BeforeEach
    void setUp() {
        kisTokenCacheQueryService = new KisTokenCacheQueryService(kisClient, cacheManager);
    }

    @Test
    @DisplayName("KIS 접근토큰 발급에 성공하면 accessToken 문자열만 반환한다")
    void getAccessToken() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisAccessTokenResponse("issued-token", "Bearer", 86400f, "2026-01-01 00:00:00"));

        // when
        String result = kisTokenCacheQueryService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("issued-token");
    }

    @Test
    @DisplayName("KIS 토큰 발급에 실패하면 예외를 그대로 전파한다")
    void getAccessTokenFailsWhenKisTokenIssueFails() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisClient.getAccessToken("app-key", "secret-key", AccountType.REAL))
                .willThrow(new BusinessException(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED));

        // when & then
        assertThatThrownBy(() ->
                kisTokenCacheQueryService.getAccessToken(userId, "app-key", "secret-key", AccountType.REAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED);
                });
    }

    @Test
    @DisplayName("KIS 접속키 발급에 성공하면 approvalKey 문자열만 반환한다")
    void getApprovalKey() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisClient.getApprovalKey("app-key", "secret-key", AccountType.REAL))
                .willReturn(new KisApprovalKeyResponse("issued-approval-key"));

        // when
        String result = kisTokenCacheQueryService.getApprovalKey(userId, "app-key", "secret-key", AccountType.REAL);

        // then
        assertThat(result).isEqualTo("issued-approval-key");
    }

    @Test
    @DisplayName("KIS 접속키 발급에 실패하면 예외를 그대로 전파한다")
    void getApprovalKeyFailsWhenKisIssueFails() {
        // given
        UUID userId = UUID.randomUUID();
        given(kisClient.getApprovalKey("app-key", "secret-key", AccountType.REAL))
                .willThrow(new BusinessException(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED));

        // when & then
        assertThatThrownBy(() ->
                kisTokenCacheQueryService.getApprovalKey(userId, "app-key", "secret-key", AccountType.REAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.KIS_TOKEN_ISSUE_FAILED);
                });
    }

    @Test
    @DisplayName("접근토큰 캐시에 값이 있으면 KIS 호출 없이 그 값을 반환한다")
    void peekAccessTokenReturnsCachedValue() {
        // given
        UUID userId = UUID.randomUUID();
        given(cacheManager.getCache("kis-access-token")).willReturn(cache);
        given(cache.get(userId, String.class)).willReturn("cached-token");

        // when
        Optional<String> result = kisTokenCacheQueryService.peekAccessToken(userId);

        // then
        assertThat(result).contains("cached-token");
    }

    @Test
    @DisplayName("접근토큰 캐시에 값이 없으면 빈 Optional을 반환한다")
    void peekAccessTokenReturnsEmptyWhenCacheMiss() {
        // given
        UUID userId = UUID.randomUUID();
        given(cacheManager.getCache("kis-access-token")).willReturn(cache);
        given(cache.get(userId, String.class)).willReturn(null);

        // when
        Optional<String> result = kisTokenCacheQueryService.peekAccessToken(userId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("캐시 자체가 없으면(설정 누락 등) 빈 Optional을 반환한다")
    void peekAccessTokenReturnsEmptyWhenCacheNotConfigured() {
        // given
        UUID userId = UUID.randomUUID();
        given(cacheManager.getCache("kis-access-token")).willReturn(null);

        // when
        Optional<String> result = kisTokenCacheQueryService.peekAccessToken(userId);

        // then
        assertThat(result).isEmpty();
    }
}
