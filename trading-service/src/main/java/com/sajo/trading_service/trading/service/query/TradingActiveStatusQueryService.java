package com.sajo.trading_service.trading.service.query;

import com.sajo.trading_service.trading.controller.dto.response.TradingActiveStatusResponse;
import com.sajo.trading_service.trading.repository.query.AutoTradingQueryRepository;
import com.sajo.trading_service.trading.repository.query.OrderQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingActiveStatusQueryService {

    private final AutoTradingQueryRepository autoTradingQueryRepository;
    private final OrderQueryRepository orderQueryRepository;

    public TradingActiveStatusResponse getActiveStatus(UUID userId) {

        boolean hasActiveAutoTrading =
                autoTradingQueryRepository
                        .existsByUserIdAndEnabledTrueAndDeletedAtIsNull(userId);

        if (hasActiveAutoTrading) {
            return new TradingActiveStatusResponse(true);
        }

        boolean hasActiveOrder =
                orderQueryRepository.existsActiveOrderByUserId(userId);

        return new TradingActiveStatusResponse(hasActiveOrder);
    }
}