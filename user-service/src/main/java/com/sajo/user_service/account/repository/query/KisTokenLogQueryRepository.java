package com.sajo.user_service.account.repository.query;

import com.sajo.user_service.account.domain.KisTokenLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KisTokenLogQueryRepository extends JpaRepository<KisTokenLog, UUID> {

    // 특정 사용자의 전체 이벤트를 시간순으로 (관리자용 상세 이력 조회)
    Page<KisTokenLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
