package com.sajo.user_service.account.repository.query;

import com.sajo.user_service.account.domain.KisTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// 전체 유저 대상 목록 조회는 findAll(Pageable)로 충분함 - row 수가 (유저 수 x 토큰타입 수)로
// 고정돼 있어 p_kis_token_logs처럼 이력이 쌓여도 느려지지 않음.
public interface KisTokenStatusQueryRepository extends JpaRepository<KisTokenStatus, UUID> {
}
