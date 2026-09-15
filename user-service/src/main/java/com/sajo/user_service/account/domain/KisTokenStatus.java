package com.sajo.user_service.account.domain;

import com.sajo.common.entity.BaseUpdatableEntity;
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

import java.time.Instant;
import java.util.UUID;

// 사용자+토큰타입별 "현재 상태" 스냅샷 - p_kis_token_logs(append-only 이력)와 짝을 이룸.
// 관리자 목록 조회(getTokenStatuses)가 이력 전체를 훑지 않고 이 테이블만 보게 하기 위한 용도.
// KisTokenLogCommandService가 로그를 남길 때마다 이 row도 같이 upsert한다.
// (user_id, token_type) unique 제약은 V4__create_kis_token_status_domain.sql에 선언돼 있음
@Getter
@Entity
@Table(name = "p_kis_token_status")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KisTokenStatus extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private KisTokenType tokenType;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    private String errorCode;

    private String errorMessage;

    @Column(nullable = false)
    private Instant lastEventAt;
}
