package com.sajo.user_service.account.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.crypto.HmacSha256Hasher;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.repository.command.AccountCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountCommandServiceTest {

    @Mock
    private AccountCommandRepository accountCommandRepository;

    @Mock
    private HmacSha256Hasher hmacSha256Hasher;

    private AccountCommandService accountCommandService;

    @BeforeEach
    void setUp() {
        accountCommandService = new AccountCommandService(accountCommandRepository, hmacSha256Hasher);
    }

    @Test
    @DisplayName("계좌를 생성하면 userId와 계좌번호 해시가 함께 저장된다")
    void createAccount() {
        // given
        UUID userId = UUID.randomUUID();

        given(hmacSha256Hasher.hash("12345678-01")).willReturn("hashed-account-no");
        given(accountCommandRepository.existsByUserIdAndDeletedAtIsNull(userId)).willReturn(false);
        given(accountCommandRepository.existsByAccountNoHashAndDeletedAtIsNull("hashed-account-no")).willReturn(false);
        given(accountCommandRepository.saveAndFlush(any(Account.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        Account account = accountCommandService.createAccount(
                userId, "app-key", "secret-key", "12345678-01", AccountType.REAL);

        // then
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountCommandRepository).saveAndFlush(captor.capture());

        Account savedAccount = captor.getValue();
        assertThat(savedAccount.getUserId()).isEqualTo(userId);
        assertThat(savedAccount.getAppKey()).isEqualTo("app-key");
        assertThat(savedAccount.getSecretKey()).isEqualTo("secret-key");
        assertThat(savedAccount.getAccountNo()).isEqualTo("12345678-01");
        assertThat(savedAccount.getAccountNoHash()).isEqualTo("hashed-account-no");
        assertThat(savedAccount.getAccountType()).isEqualTo(AccountType.REAL);

        assertThat(account.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("이미 계좌가 있는 유저면 ALREADY_HAS_ACCOUNT 예외를 던지고 저장하지 않는다")
    void createAccountAlreadyHasAccount() {
        // given
        UUID userId = UUID.randomUUID();

        given(accountCommandRepository.existsByUserIdAndDeletedAtIsNull(userId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> accountCommandService.createAccount(
                userId, "app-key", "secret-key", "12345678-01", AccountType.REAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.ALREADY_HAS_ACCOUNT);
                });

        verify(accountCommandRepository, never()).saveAndFlush(any(Account.class));
    }

    @Test
    @DisplayName("이미 등록된 계좌번호면 DUPLICATE_ACCOUNT_NO 예외를 던지고 저장하지 않는다")
    void createAccountDuplicateAccountNo() {
        // given
        UUID userId = UUID.randomUUID();

        given(hmacSha256Hasher.hash("12345678-01")).willReturn("hashed-account-no");
        given(accountCommandRepository.existsByUserIdAndDeletedAtIsNull(userId)).willReturn(false);
        given(accountCommandRepository.existsByAccountNoHashAndDeletedAtIsNull("hashed-account-no")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> accountCommandService.createAccount(
                userId, "app-key", "secret-key", "12345678-01", AccountType.REAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.DUPLICATE_ACCOUNT_NO);
                });

        verify(accountCommandRepository, never()).saveAndFlush(any(Account.class));
    }

    @Test
    @DisplayName("동시 요청으로 DB 유니크 제약을 위반하면 DUPLICATE_ACCOUNT_REQUEST 예외로 변환한다")
    void createAccountConcurrentRequestConflict() {
        // given
        UUID userId = UUID.randomUUID();

        given(hmacSha256Hasher.hash("12345678-01")).willReturn("hashed-account-no");
        given(accountCommandRepository.existsByUserIdAndDeletedAtIsNull(userId)).willReturn(false);
        given(accountCommandRepository.existsByAccountNoHashAndDeletedAtIsNull("hashed-account-no")).willReturn(false);
        given(accountCommandRepository.saveAndFlush(any(Account.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

        // when & then
        assertThatThrownBy(() -> accountCommandService.createAccount(
                userId, "app-key", "secret-key", "12345678-01", AccountType.REAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.DUPLICATE_ACCOUNT_REQUEST);
                });
    }

    @Test
    @DisplayName("계좌를 삭제하면 softDelete가 반영된 계좌를 반환한다")
    void deleteAccount() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "123-456-789", "hashed-account-no", AccountType.REAL);
        given(accountCommandRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(account));

        // when
        Account result = accountCommandService.deleteAccount(userId);

        // then
        assertThat(result).isSameAs(account);
        assertThat(result.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제할 계좌가 없으면 ACCOUNT_NOT_FOUND 예외를 던진다")
    void deleteAccountNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        given(accountCommandRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> accountCommandService.deleteAccount(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
                });
    }
}
