package com.sajo.user_service.account.domain;

import com.sajo.common.entity.BaseEntity;
import jakarta.persistence.Column;
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
@Table(name = "p_kis_token_logs")
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KisTokenLog extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    private String errorCode; // KIS가 발급한 에러 코드

    private String errorMessage; // KIS가 발급한 에러 메시지

    private KisTokenLog(UUID accountId, UUID userId, EventType eventType, String errorCode, String errorMessage) {
        this.accountId = accountId;
        this.userId = userId;
        this.eventType = eventType;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static KisTokenLog createTokenLog(
            UUID accountId, UUID userId, EventType eventType, String errorCode, String errorMessage
    ) {

        return new KisTokenLog(
                accountId,
                userId,
                eventType,
                errorCode,
                errorMessage
        );
    }

}
