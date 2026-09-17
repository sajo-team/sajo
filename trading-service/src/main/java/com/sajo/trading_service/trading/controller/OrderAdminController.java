package com.sajo.trading_service.trading.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.common.response.PageResponse;
import com.sajo.trading_service.trading.controller.dto.request.OrderAdminSearchCondition;
import com.sajo.trading_service.trading.controller.dto.request.OrderManualResolutionRequest;
import com.sajo.trading_service.trading.controller.dto.response.OrderAdminListResponse;
import com.sajo.trading_service.trading.service.command.OrderAdminCommandService;
import com.sajo.trading_service.trading.service.query.OrderQueryService;
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
@RequestMapping("/api/v1/admin/trading/orders")
public class OrderAdminController {

    private final OrderQueryService orderQueryService;
    private final OrderAdminCommandService orderAdminCommandService;

    @GetMapping
    public ResponseEntity<GeneralResponse<PageResponse<OrderAdminListResponse>>> getAllOrders(
            @RequestHeader("X-User-Role") String role,
            @ModelAttribute OrderAdminSearchCondition condition,
            Pageable pageable
    ) {
        if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException("관리자 권한이 필요합니다");
        }

        Page<OrderAdminListResponse> page =
                orderQueryService.findAllOrdersForAdmin(
                        condition,
                        pageable
                );

        PageResponse<OrderAdminListResponse> response =
                PageResponse.from(page);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }

    @PatchMapping("/{orderId}/resolutions")
    public ResponseEntity<GeneralResponse<Void>> resolveTimeoutOrder(
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderManualResolutionRequest request
    ) {
        if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException("관리자 권한이 필요합니다.");
        }

        orderAdminCommandService.resolveTimeoutOrder(
                orderId,
                request
        );

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                null
        );
    }
}