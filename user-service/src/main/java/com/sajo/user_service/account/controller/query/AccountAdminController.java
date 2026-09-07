package com.sajo.user_service.account.controller.query;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.common.response.PageResponse;
import com.sajo.user_service.account.controller.dto.response.TokenEventResponse;
import com.sajo.user_service.account.controller.dto.response.TokenStatusResponse;
import com.sajo.user_service.account.service.query.KisTokenLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// TODO: 관리자 권한 검증
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AccountAdminController {

    private final KisTokenLogQueryService kisTokenLogQueryService;

    // 전체 사용자 대상, 사용자별 최신 토큰 발급 상태 1건씩 목록 조회
    @GetMapping("/accounts/token-status")
    public ResponseEntity<GeneralResponse<PageResponse<TokenStatusResponse>>> getTokenStatuses(Pageable pageable) {
        Page<TokenStatusResponse> page = kisTokenLogQueryService.getTokenStatuses(pageable);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, PageResponse.from(page));
    }

    // 특정 사용자의 모든 토큰 발급 로그 조회
    @GetMapping("/accounts/{userId}/token-status/history")
    public ResponseEntity<GeneralResponse<PageResponse<TokenEventResponse>>> getTokenEventHistory(
            @PathVariable UUID userId,
            Pageable pageable
    ) {
        Page<TokenEventResponse> page = kisTokenLogQueryService.getTokenEventHistory(userId, pageable);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, PageResponse.from(page));
    }
}
