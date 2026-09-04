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

@Getter
@Entity
@Table(name = "p_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
//Todo: DDL 작성 시 partial unique index 적용
public class Account extends BaseUpdatableEntity {

    private static final String ACCOUNT_NO_PATTERN = "^[0-9]{8}-[0-9]{2}$";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
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

    @Column(unique = true, nullable = false)
    private String accountNoHash;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    private AccountType accountType;

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
}
