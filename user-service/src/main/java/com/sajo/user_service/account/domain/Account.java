package com.sajo.user_service.account.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.account.crypto.AesGcmStringConverter;
import com.sajo.user_service.account.exception.AccountErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

// user_id/account_no_hash 유니크 제약은 JPA가 아니라 V3 Flyway 마이그레이션의
// partial unique index(WHERE deleted_at IS NULL)로 DB 레벨에서만 강제된다.
@Getter
@Entity
@Table(name = "p_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseUpdatableEntity {

    private static final String ACCOUNT_NO_PATTERN = "^[0-9]{8}-[0-9]{2}$";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Convert(converter = AesGcmStringConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private String appKey;

    @Convert(converter = AesGcmStringConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private String secretKey;

    @Convert(converter = AesGcmStringConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private String accountNo;

    @Column(nullable = false)
    private String accountNoHash;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private AccountType accountType;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private AccountStatus status;

    private Account(
            UUID userId, String appKey, String secretKey, String accountNo, String accountNoHash,
            AccountType accountType
    ) {
        validateAccountNoFormat(accountNo);
        this.userId = userId;
        this.appKey = appKey;
        this.secretKey = secretKey;
        this.accountNo = accountNo;
        this.accountNoHash = accountNoHash;
        this.accountType = accountType;
        this.status = AccountStatus.ACTIVE;
    }

    public static Account createAccount(
            UUID userId, String appKey, String secretKey, String accountNo, String accountNoHash,
            AccountType accountType) {
        return new Account(userId, appKey, secretKey, accountNo, accountNoHash, accountType);
    }

    public String getCano() {
        validateAccountNoFormat(accountNo);
        return accountNo.substring(0, 8);
    }

    public String getAccountProductCode() {
        validateAccountNoFormat(accountNo);
        return accountNo.substring(9, 11);
    }

    private static void validateAccountNoFormat(String accountNo) {
        if (accountNo == null || !accountNo.matches(ACCOUNT_NO_PATTERN)) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_NO_FORMAT);
        }
    }
    @Override
    public void softDelete(UUID deletedBy) {
        super.softDelete(deletedBy);
        this.status = AccountStatus.DELETED;
    }

    // 계좌 삭제 전 trading-service의 활성 자동매매/미체결 주문 여부를 확인하는 동안,
    // 그 확인-삭제 사이의 짧은 창에 새 주문이 이 계좌 정보를 가져가지 못하도록 먼저
    // PENDING_DELETION으로 표시한다 (AccountQueryService.getAccountByUserId에서 차단).
    public void markPendingDeletion() {
        this.status = AccountStatus.PENDING_DELETION;
    }

    // 활성 거래가 있어 삭제가 취소된 경우 원상복구
    public void reactivate() {
        this.status = AccountStatus.ACTIVE;
    }
}
