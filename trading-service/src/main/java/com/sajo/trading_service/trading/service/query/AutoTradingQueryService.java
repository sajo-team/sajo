package com.sajo.trading_service.trading.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingAdminSearchCondition;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingAdminResponse;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingQueryResponse;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.domain.Order;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.query.AutoTradingQueryRepository;
import com.sajo.trading_service.trading.repository.query.OrderQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutoTradingQueryService {
    private final AutoTradingQueryRepository autoTradingQueryRepository;
    private final OrderQueryRepository orderQueryRepository;

    public Page<AutoTradingQueryResponse> findAllByUserId(
            UUID userId,
            Pageable pageable
    ) {
        Page<AutoTrading> autoTradingPage =
                autoTradingQueryRepository
                        .findAllByUserIdAndDeletedAtIsNull(
                                userId,
                                pageable
                        );

        if (autoTradingPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> autoTradingIds =
                autoTradingPage.getContent()
                        .stream()
                        .map(AutoTrading::getId)
                        .toList();

        Map<UUID, Order> latestOrderMap =
                orderQueryRepository
                        .findLatestOrdersByAutoTradingIds(autoTradingIds)
                        .stream()
                        .collect(Collectors.toMap(
                                Order::getAutoTradingId,
                                order -> order
                        ));

        return autoTradingPage.map(autoTrading ->
                AutoTradingQueryResponse.from(
                        autoTrading,
                        latestOrderMap.get(autoTrading.getId())
                )
        );
    }

    public AutoTradingQueryResponse findById(
            UUID autoTradingId,
            UUID userId
    ){
        AutoTrading autoTrading =
                autoTradingQueryRepository
                        .findByIdAndUserIdAndDeletedAtIsNull(
                                autoTradingId,
                                userId
                        )
                        .orElseThrow(()->
                                new BusinessException(
                                        TradingErrorCode.AUTO_TRADING_NOT_FOUND
                                )
                        );

        Order lastOrder = orderQueryRepository
                .findFirstByAutoTradingIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        autoTradingId
                )
                .orElse(null);

        return AutoTradingQueryResponse.from(
                autoTrading,
                lastOrder
                );
    }

    public Page<AutoTradingAdminResponse> findAllAutoTradingForAdmin(
            AutoTradingAdminSearchCondition condition,
            Pageable pageable
    ) {
        Page<AutoTrading> autoTradingPage =
                autoTradingQueryRepository.findAllForAdmin(
                        condition.userId(),
                        condition.strategyId(),
                        condition.direction(),
                        condition.enabled(),
                        pageable
                );

        if (autoTradingPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> autoTradingIds =
                autoTradingPage.getContent()
                        .stream()
                        .map(AutoTrading::getId)
                        .toList();

        Map<UUID, Order> latestOrderMap =
                orderQueryRepository
                        .findLatestOrdersByAutoTradingIds(autoTradingIds)
                        .stream()
                        .collect(Collectors.toMap(
                                Order::getAutoTradingId,
                                order -> order
                        ));

        return autoTradingPage.map(autoTrading ->
                AutoTradingAdminResponse.from(
                        autoTrading,
                        latestOrderMap.get(autoTrading.getId())
                )
        );
    }
}
