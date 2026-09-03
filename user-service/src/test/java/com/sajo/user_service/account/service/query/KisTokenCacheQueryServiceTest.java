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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class KisTokenCacheQueryServiceTest {

    @Mock
    private KisClient kisClient;

    private KisTokenCacheQueryService kisTokenCacheQueryService;

    @BeforeEach
    void setUp() {
        kisTokenCacheQueryService = new KisTokenCacheQueryService(kisClient);
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
}
