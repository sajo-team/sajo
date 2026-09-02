package com.sajo.user_service.account.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.crypto.HmacSha256Hasher;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.repository.query.AccountQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AccountQueryServiceTest {

    @Mock
    private AccountQueryRepository accountQueryRepository;

    @Mock
    private HmacSha256Hasher hmacSha256Hasher;

    private AccountQueryService accountQueryService;

    @BeforeEach
    void setUp() {
        accountQueryService = new AccountQueryService(accountQueryRepository, hmacSha256Hasher);
    }

    @Test
    @DisplayName("중복이 없으면 예외를 던지지 않는다")
    void validateCreatablePasses() {
        // given
        UUID userId = UUID.randomUUID();

        given(accountQueryRepository.existsByUserId(userId)).willReturn(false);
        given(hmacSha256Hasher.hash("123-456-789")).willReturn("hashed-account-no");
        given(accountQueryRepository.existsByAccountNoHash("hashed-account-no")).willReturn(false);

        // when & then
        assertThatCode(() -> accountQueryService.validateCreatable(userId, "123-456-789"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미 계좌가 있는 유저면 ALREADY_HAS_ACCOUNT 예외를 던진다")
    void validateCreatableFailsWhenUserAlreadyHasAccount() {
        // given
        UUID userId = UUID.randomUUID();
        given(accountQueryRepository.existsByUserId(userId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> accountQueryService.validateCreatable(userId, "123-456-789"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.ALREADY_HAS_ACCOUNT);
                });
    }

    @Test
    @DisplayName("이미 등록된 계좌번호면 DUPLICATE_ACCOUNT_NO 예외를 던진다")
    void validateCreatableFailsWhenAccountNoDuplicate() {
        // given
        UUID userId = UUID.randomUUID();
        given(accountQueryRepository.existsByUserId(userId)).willReturn(false);
        given(hmacSha256Hasher.hash("123-456-789")).willReturn("hashed-account-no");
        given(accountQueryRepository.existsByAccountNoHash("hashed-account-no")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> accountQueryService.validateCreatable(userId, "123-456-789"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.DUPLICATE_ACCOUNT_NO);
                });
    }
}
