package com.sajo.user_service.account.domain;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.exception.AccountErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    @DisplayName("accountNo 형식이 올바르면 계좌 생성에 성공한다")
    void createAccountSucceedsWithValidAccountNo() {
        // given & when
        Account account = Account.createAccount(
                UUID.randomUUID(), "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);

        // then
        assertThat(account.getCano()).isEqualTo("12345678");
        assertThat(account.getAccountProductCode()).isEqualTo("01");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("markPendingDeletion 호출 시 상태가 PENDING_DELETION으로 바뀐다")
    void markPendingDeletionChangesStatus() {
        // given
        Account account = Account.createAccount(
                UUID.randomUUID(), "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);

        // when
        account.markPendingDeletion();

        // then
        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING_DELETION);
    }

    @Test
    @DisplayName("reactivate 호출 시 상태가 ACTIVE로 되돌아간다")
    void reactivateRestoresActiveStatus() {
        // given
        Account account = Account.createAccount(
                UUID.randomUUID(), "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        account.markPendingDeletion();

        // when
        account.reactivate();

        // then
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("softDelete 호출 시 상태가 DELETED로 바뀐다")
    void softDeleteChangesStatusToDeleted() {
        // given
        Account account = Account.createAccount(
                UUID.randomUUID(), "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        account.markPendingDeletion();

        // when
        account.softDelete(account.getUserId());

        // then
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DELETED);
    }

    @Test
    @DisplayName("accountNo 형식이 올바르지 않으면 계좌 생성 시 INVALID_ACCOUNT_NO_FORMAT 예외를 던진다")
    void createAccountFailsWithInvalidAccountNo() {
        // when & then
        assertThatThrownBy(() -> Account.createAccount(
                UUID.randomUUID(), "app-key", "secret-key", "123-456-789", "hashed-account-no", AccountType.REAL))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_NO_FORMAT);
                });
    }

    @Test
    @DisplayName("생성자 검증을 우회해 저장된 레거시 accountNo(형식 불일치)는 getCano() 호출 시 예외를 던진다")
    void getCanoFailsWhenPersistedAccountNoHasInvalidFormat() {
        // given
        // JPA는 하이버네이트 프록시/리플렉션으로 필드를 채우기 때문에 생성자 검증을 우회할 수 있다.
        // 마이그레이션/레거시 데이터로 인해 이런 상태가 저장돼 있을 가능성을 시뮬레이션한다.
        Account account = Account.createAccount(
                UUID.randomUUID(), "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        ReflectionTestUtils.setField(account, "accountNo", "123-456-789");

        // when & then
        assertThatThrownBy(account::getCano)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_NO_FORMAT);
                });
    }

    @Test
    @DisplayName("생성자 검증을 우회해 저장된 레거시 accountNo(형식 불일치)는 getAccountProductCode() 호출 시 예외를 던진다")
    void getAccountProductCodeFailsWhenPersistedAccountNoHasInvalidFormat() {
        // given
        Account account = Account.createAccount(
                UUID.randomUUID(), "app-key", "secret-key", "12345678-01", "hashed-account-no", AccountType.REAL);
        ReflectionTestUtils.setField(account, "accountNo", "123-456-789");

        // when & then
        assertThatThrownBy(account::getAccountProductCode)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_NO_FORMAT);
                });
    }
}
