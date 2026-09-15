package com.sajo.user_service.account.service.query;

import com.sajo.user_service.account.controller.dto.response.TokenEventResponse;
import com.sajo.user_service.account.controller.dto.response.TokenStatusResponse;
import com.sajo.user_service.account.repository.query.KisTokenLogQueryRepository;
import com.sajo.user_service.account.repository.query.KisTokenStatusQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KisTokenLogQueryService {

    private final KisTokenLogQueryRepository kisTokenLogQueryRepository;
    private final KisTokenStatusQueryRepository kisTokenStatusQueryRepository;

    // 관리자용 - 사용자별 최신 토큰 발급 상태 목록
    // p_kis_token_status(현재 상태 스냅샷)를 조회 - row 수가 유저 수만큼만 고정돼있어
    // 정렬 강제는 리포지토리 쿼리 자체에 고정돼 있음 (KisTokenStatusQueryRepository 참고)
    @Transactional(readOnly = true)
    public Page<TokenStatusResponse> getTokenStatuses(Pageable pageable) {
        return kisTokenStatusQueryRepository.findAllByOrderByLastEventAtDescIdDesc(pageable)
                .map(TokenStatusResponse::from);
    }

    // 관리자용 - 특정 사용자의 토큰 발급 이력 (시간순)
    @Transactional(readOnly = true)
    public Page<TokenEventResponse> getTokenEventHistory(UUID userId, Pageable pageable) {
        return kisTokenLogQueryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(TokenEventResponse::from);
    }
}
