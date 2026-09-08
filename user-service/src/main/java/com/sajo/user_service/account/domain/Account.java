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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uq_account_user_id", columnNames = {"user_id", "unique_column"}),
        @UniqueConstraint(name = "uq_account_no_hash", columnNames = {"account_no_hash", "unique_column"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
//Todo: DDL 작성 시 partial unique index 적용
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

    // soft delete된 row끼리는 유니크 제약에서 서로 겹치지 않도록 하기 위한 컬럼
    // 활성 상태: 고정값(0) 공유, 삭제 상태: 자기 자신의 id로 교체
    // 추후 ddl 작성 하게 되면 PostgreSQL partial unique index로 교체
    //Todo: DDL 작성 시 partial unique index 적용
    @Column(name = "unique_column", nullable = false)
    private UUID uniqueColumn;

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
        this.uniqueColumn = new UUID(0L, 0L);
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
        this.uniqueColumn = this.id;
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
