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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AccountAdminController {

    private final KisTokenLogQueryService kisTokenLogQueryService;

    // 전체 사용자 대상, 사용자별 최신 토큰 발급 상태 1건씩 목록 조회
    @GetMapping("/accounts/token-status")
    public ResponseEntity<GeneralResponse<PageResponse<TokenStatusResponse>>> getTokenStatuses(
            @RequestHeader("X-User-Role") String role,
            Pageable pageable
    ) {
        // Gateway가 검증한 X-User-Role만 신뢰한다 (Principal/@PreAuthorize 기반 검증은
        // 별도 인프라 작업 - 그 전까지는 헤더 값을 직접 확인)
        if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException("관리자 권한이 필요합니다");
        }

        Page<TokenStatusResponse> page = kisTokenLogQueryService.getTokenStatuses(pageable);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, PageResponse.from(page));
    }

    // 특정 사용자의 모든 토큰 발급 로그 조회
    @GetMapping("/accounts/{userId}/token-status/history")
    public ResponseEntity<GeneralResponse<PageResponse<TokenEventResponse>>> getTokenEventHistory(
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID userId,
            Pageable pageable
    ) {
        if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException("관리자 권한이 필요합니다");
        }

        Page<TokenEventResponse> page = kisTokenLogQueryService.getTokenEventHistory(userId, pageable);
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, PageResponse.from(page));
    }
}
