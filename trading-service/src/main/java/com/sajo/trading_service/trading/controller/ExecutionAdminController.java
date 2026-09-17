package com.sajo.trading_service.trading.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.common.response.PageResponse;
import com.sajo.trading_service.trading.controller.dto.request.ExecutionAdminSearchCondition;
import com.sajo.trading_service.trading.controller.dto.response.ExecutionAdminResponse;
import com.sajo.trading_service.trading.service.query.ExecutionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/trading/executions")
public class ExecutionAdminController {

    private final ExecutionQueryService executionQueryService;

    @GetMapping
    public ResponseEntity<GeneralResponse<PageResponse<ExecutionAdminResponse>>> getAllExecutions(
            @RequestHeader("X-User-Role") String role,
            @ModelAttribute ExecutionAdminSearchCondition condition,
            Pageable pageable
    ) {
        if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException("관리자 권한이 필요합니다.");
        }

        Page<ExecutionAdminResponse> page =
                executionQueryService.findAllExecutionsForAdmin(
                        condition,
                        pageable
                );

        PageResponse<ExecutionAdminResponse> response =
                PageResponse.from(page);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }
}
