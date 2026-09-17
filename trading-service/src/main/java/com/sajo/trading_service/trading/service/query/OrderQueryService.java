package com.sajo.trading_service.trading.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.request.OrderAdminSearchCondition;
import com.sajo.trading_service.trading.controller.dto.request.OrderSearchCondition;
import com.sajo.trading_service.trading.controller.dto.response.OrderAdminListResponse;
import com.sajo.trading_service.trading.controller.dto.response.OrderDetailResponse;
import com.sajo.trading_service.trading.controller.dto.response.OrderListResponse;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.query.OrderQueryRepository;
import com.sajo.trading_service.trading.repository.query.specification.OrderSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {
    private final OrderQueryRepository orderQueryRepository;

    public Page<OrderListResponse> findOrdersByUserId(
            UUID userId,
            OrderSearchCondition condition,
            Pageable pageable
    ) {
        return orderQueryRepository
                .findAll(
                        OrderSpecifications.withCondition(userId, condition),
                        pageable
                )
                .map(OrderListResponse::from);
    }

    public OrderDetailResponse findOrderByIdAndUserId(
            UUID orderId,
            UUID userId
    ){
        Order order =
                orderQueryRepository
                        .findByIdAndUserId(orderId, userId)
                        .orElseThrow(()->
                                new BusinessException(
                                        TradingErrorCode.ORDER_NOT_FOUND
                                )
                        );
        return OrderDetailResponse.from(order);
    }

    public Page<OrderAdminListResponse> findAllOrdersForAdmin(
            OrderAdminSearchCondition condition,
            Pageable pageable
    ) {
        return orderQueryRepository
                .findAll(
                        OrderSpecifications.withAdminCondition(condition),
                        pageable
                )
                .map(OrderAdminListResponse::from);
    }
}
