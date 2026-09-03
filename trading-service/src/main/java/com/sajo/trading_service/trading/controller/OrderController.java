package com.sajo.trading_service.trading.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.common.response.PageResponse;
import com.sajo.trading_service.trading.controller.dto.response.OrderDetailResponse;
import com.sajo.trading_service.trading.controller.dto.response.OrderListResponse;
import com.sajo.trading_service.trading.service.query.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderQueryService orderQueryService;

    @GetMapping
    public ResponseEntity<GeneralResponse<PageResponse<OrderListResponse>>> getAllOrders(
            @RequestParam("userId") UUID userId, // TODO: Gateway에서 JWT 검증 후 전달하는 X-User-Id 헤더를 사용하도록 변경
            Pageable pageable
    ){
        Page<OrderListResponse> page =
                orderQueryService.findOrdersByUserId(
                        userId,
                        pageable
                );

        PageResponse<OrderListResponse> response =
                PageResponse.from(page);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<GeneralResponse<OrderDetailResponse>> getOrderDetail(
            @PathVariable UUID orderId,
            @RequestParam("userId") UUID userId // TODO: Gateway에서 JWT 검증 후 전달하는 X-User-Id 헤더를 사용하도록 변경
    ){
        OrderDetailResponse response =
                orderQueryService.findOrderByIdAndUserId(
                        orderId,
                        userId
                );

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }
}
