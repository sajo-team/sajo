package com.sajo.user_service.account.repository.query;

import com.sajo.user_service.account.domain.KisTokenLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface KisTokenLogQueryRepository extends JpaRepository<KisTokenLog, UUID> {

    // 사용자+토큰타입 조합별 최신 이벤트 1건만 (관리자용 목록 조회)
    // 토큰타입 구분 없이 userId로만 묶으면, 한쪽 타입(예: 접속키)의 최신 이벤트가
    // 다른쪽(예: 접근토큰)의 문제 상태를 가려버릴 수 있어 반드시 함께 묶어야 한다
    @Query("""
            SELECT e FROM KisTokenLog e
            WHERE e.createdAt = (
                SELECT MAX(e2.createdAt) FROM KisTokenLog e2
                WHERE e2.userId = e.userId AND e2.tokenType = e.tokenType
            )
            """)
    Page<KisTokenLog> findLatestPerUser(Pageable pageable);

    // 특정 사용자의 전체 이벤트를 시간순으로 (관리자용 상세 이력 조회)
    Page<KisTokenLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
