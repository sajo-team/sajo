package com.sajo.user_service.account.repository.query;

import com.sajo.user_service.account.domain.KisTokenLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface KisTokenLogQueryRepository extends JpaRepository<KisTokenLog, UUID> {

    // 사용자+토큰타입 조합별 최신 이벤트 1건만 (관리자용 목록 조회)
    // createdAt 동시각 tie 시 중복 반환을 막기 위해 id를 2차 정렬 기준으로 사용
    @Query("""
            SELECT e FROM KisTokenLog e
            WHERE NOT EXISTS (
                SELECT 1 FROM KisTokenLog e2
                WHERE e2.userId = e.userId AND e2.tokenType = e.tokenType
                AND (e2.createdAt > e.createdAt
                     OR (e2.createdAt = e.createdAt AND e2.id > e.id))
            )
            """)
    Page<KisTokenLog> findLatestPerUser(Pageable pageable);

    // 특정 사용자의 전체 이벤트를 시간순으로 (관리자용 상세 이력 조회)
    Page<KisTokenLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
