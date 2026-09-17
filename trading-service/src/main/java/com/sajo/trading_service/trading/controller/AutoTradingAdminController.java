package com.sajo.trading_service.trading.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.common.response.PageResponse;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingAdminSearchCondition;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingAdminSuspensionRequest;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingAdminResponse;
import com.sajo.trading_service.trading.service.command.AutoTradingAdminCommandService;
import com.sajo.trading_service.trading.service.query.AutoTradingQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/trading/auto-tradings")
public class AutoTradingAdminController {
    private final AutoTradingQueryService autoTradingQueryService;
    private final AutoTradingAdminCommandService autoTradingAdminCommandService;

    @GetMapping
    public ResponseEntity<GeneralResponse<PageResponse<AutoTradingAdminResponse>>> getAllAutoTradings(
            @RequestHeader("X-User-Role") String role,
            @ModelAttribute AutoTradingAdminSearchCondition condition,
            Pageable pageable
    ) {
        if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException("관리자 권한이 필요합니다.");
        }

        Page<AutoTradingAdminResponse> page =
                autoTradingQueryService.findAllAutoTradingForAdmin(
                        condition,
                        pageable
                );

        PageResponse<AutoTradingAdminResponse> response =
                PageResponse.from(page);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }

    @PatchMapping("/{autoTradingId}/suspensions")
    public ResponseEntity<GeneralResponse<Void>> updateSuspension(
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID autoTradingId,
            @Valid @RequestBody AutoTradingAdminSuspensionRequest request
    ) {
        if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException("관리자 권한이 필요합니다.");
        }

        if (Boolean.TRUE.equals(request.suspended())) {
            autoTradingAdminCommandService.suspend(autoTradingId);
        } else {
            autoTradingAdminCommandService.resume(autoTradingId);
        }

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                null
        );
    }
}
