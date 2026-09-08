package com.sajo.user_service.account.service.query;

import com.sajo.user_service.account.controller.dto.response.TokenEventResponse;
import com.sajo.user_service.account.controller.dto.response.TokenStatusResponse;
import com.sajo.user_service.account.repository.query.KisTokenLogQueryRepository;
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

    // 관리자용 - 사용자별 최신 토큰 발급 상태 목록
    @Transactional(readOnly = true)
    public Page<TokenStatusResponse> getTokenStatuses(Pageable pageable) {
        return kisTokenLogQueryRepository.findLatestPerUser(pageable)
                .map(TokenStatusResponse::from);
    }

    // 관리자용 - 특정 사용자의 토큰 발급 이력 (시간순)
    @Transactional(readOnly = true)
    public Page<TokenEventResponse> getTokenEventHistory(UUID userId, Pageable pageable) {
        return kisTokenLogQueryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(TokenEventResponse::from);
    }
}
