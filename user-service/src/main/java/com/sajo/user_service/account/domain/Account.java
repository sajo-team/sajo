package com.sajo.user_service.account.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
import com.sajo.user_service.account.crypto.AesGcmStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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
public class Account extends BaseUpdatableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private UUID userId;

    @Convert(converter = AesGcmStringConverter.class)
    @Column(nullable = false)
    private String appKey;

    @Convert(converter = AesGcmStringConverter.class)
    @Column(nullable = false)
    private String secretKey;

    @Convert(converter = AesGcmStringConverter.class)
    @Column(nullable = false)
    private String accountNo;

    @Column(unique = true, nullable = false)
    private String accountNoHash;

    private Account(UUID userId, String appKey, String secretKey, String accountNo, String accountNoHash) {
        this.userId = userId;
        this.appKey = appKey;
        this.secretKey = secretKey;
        this.accountNo = accountNo;
        this.accountNoHash = accountNoHash;
    }

    public static Account createAccount(
            UUID userId, String appKey, String secretKey, String accountNo, String accountNoHash) {
        return new Account(userId, appKey, secretKey, accountNo, accountNoHash);
    }

}
