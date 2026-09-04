package com.sajo.user_service.account.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.controller.dto.response.AccountOrderInfoResponse;
import com.sajo.user_service.account.crypto.HmacSha256Hasher;
import com.sajo.user_service.account.domain.Account;
import com.sajo.user_service.account.domain.AccountType;
import com.sajo.user_service.account.exception.AccountErrorCode;
import com.sajo.user_service.account.repository.query.AccountQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
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

        given(accountQueryRepository.existsByUserIdAndDeletedAtIsNull(userId)).willReturn(false);
        given(hmacSha256Hasher.hash("123-456-789")).willReturn("hashed-account-no");
        given(accountQueryRepository.existsByAccountNoHashAndDeletedAtIsNull("hashed-account-no")).willReturn(false);

        // when & then
        assertThatCode(() -> accountQueryService.validateCreatable(userId, "123-456-789"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미 계좌가 있는 유저면 ALREADY_HAS_ACCOUNT 예외를 던진다")
    void validateCreatableFailsWhenUserAlreadyHasAccount() {
        // given
        UUID userId = UUID.randomUUID();
        given(accountQueryRepository.existsByUserIdAndDeletedAtIsNull(userId)).willReturn(true);

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
        given(accountQueryRepository.existsByUserIdAndDeletedAtIsNull(userId)).willReturn(false);
        given(hmacSha256Hasher.hash("123-456-789")).willReturn("hashed-account-no");
        given(accountQueryRepository.existsByAccountNoHashAndDeletedAtIsNull("hashed-account-no")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> accountQueryService.validateCreatable(userId, "123-456-789"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.DUPLICATE_ACCOUNT_NO);
                });
    }

    @Test
    @DisplayName("삭제되지 않은 계좌가 있으면 조회에 성공한다")
    void getAccountByUserIdReturnsAccount() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        given(accountQueryRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(account));

        // when
        Account result = accountQueryService.getAccountByUserId(userId);

        // then
        assertThat(result).isEqualTo(account);
    }

    @Test
    @DisplayName("계좌가 없거나 소프트 삭제된 경우 ACCOUNT_NOT_FOUND 예외를 던진다")
    void getAccountByUserIdFailsWhenAccountNotFoundOrDeleted() {
        // given
        UUID userId = UUID.randomUUID();
        given(accountQueryRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> accountQueryService.getAccountByUserId(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("주문용 계좌 정보 조회에 성공하면 cano/accountProductCode/accountType을 반환한다")
    void getAccountOrderInfoReturnsResponse() {
        // given
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        given(accountQueryRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(account));

        // when
        AccountOrderInfoResponse response = accountQueryService.getAccountOrderInfo(userId);

        // then
        assertThat(response.cano()).isEqualTo("12345678");
        assertThat(response.accountProductCode()).isEqualTo("01");
        assertThat(response.accountType()).isEqualTo("REAL");
    }

    @Test
    @DisplayName("계좌가 없거나 소프트 삭제된 경우 주문용 계좌 정보 조회도 ACCOUNT_NOT_FOUND 예외를 던진다")
    void getAccountOrderInfoFailsWhenAccountNotFoundOrDeleted() {
        // given
        UUID userId = UUID.randomUUID();
        given(accountQueryRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> accountQueryService.getAccountOrderInfo(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("저장된 accountNo 형식이 깨져있으면 주문용 계좌 정보 조회는 INVALID_ACCOUNT_NO_FORMAT 예외를 던진다")
    void getAccountOrderInfoFailsWhenPersistedAccountNoHasInvalidFormat() {
        // given
        // 생성자 검증을 우회해 레거시/마이그레이션 이슈로 형식이 깨진 accountNo가 이미 저장돼 있는 상황을 시뮬레이션한다.
        UUID userId = UUID.randomUUID();
        Account account = Account.createAccount(
                userId, "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        ReflectionTestUtils.setField(account, "accountNo", "123-456-789");
        given(accountQueryRepository.findByUserIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> accountQueryService.getAccountOrderInfo(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_NO_FORMAT);
                });
    }
}
