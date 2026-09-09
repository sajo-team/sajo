package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.feign.FeignApiException;
import com.sajo.trading_service.trading.client.StrategyClient;
import com.sajo.trading_service.trading.client.dto.response.StrategyClientResponse;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingCreateRequest;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingUpdateRequest;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingCreateResponse;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingUpdateResponse;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.AutoTradingCommandRepository;
import com.sajo.trading_service.trading.repository.command.TradingLimitCommandRepository;
import com.sajo.trading_service.trading.repository.query.OrderQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutoTradingCommandService {

    private static final String MARKET_STRATEGY_NOT_FOUND =
            "STRATEGY_0002";

    private final AutoTradingCommandRepository autoTradingCommandRepository;
    private final TradingLimitCommandRepository tradingLimitCommandRepository;
    private final StrategyClient strategyClient;
    private final AutoTradingCreateTransactionService autoTradingCreateTransactionService;
    private final OrderQueryRepository orderQueryRepository;

    public AutoTradingCreateResponse createAutoTrading(
            UUID userId,
            AutoTradingCreateRequest request
    ) {
        StrategyClientResponse strategy;

        try {
            strategy = strategyClient.getStrategy(request.strategyId());

        } catch (FeignApiException e) {
            if (e.getStatus() == 404
                    && MARKET_STRATEGY_NOT_FOUND.equals(e.getErrorCode())) {

                throw new BusinessException(
                        TradingErrorCode.STRATEGY_NOT_FOUND
                );
            }

            throw e;
        }

        if (!strategy.userId().equals(userId)) {
            throw new BusinessException(
                    TradingErrorCode.STRATEGY_NOT_FOUND
            );
        }

        return autoTradingCreateTransactionService.create(
                userId,
                request
        );
    }

    @Transactional
    public AutoTradingUpdateResponse updateAutoTrading(
            UUID userId,
            UUID autoTradingId,
            AutoTradingUpdateRequest request
    ) {
        AutoTrading autoTrading =
                autoTradingCommandRepository
                        .findByIdAndUserIdAndDeletedAtIsNull(
                                autoTradingId,
                                userId
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        TradingErrorCode.AUTO_TRADING_NOT_FOUND
                                )
                        );

        if (Boolean.TRUE.equals(request.enabled())
                && !tradingLimitCommandRepository.existsByUserId(userId)) {

            throw new BusinessException(
                    TradingErrorCode.TRADING_LIMIT_REQUIRED
            );
        }

        autoTrading.update(request.enabled());

        return AutoTradingUpdateResponse.from(autoTrading);
    }

    @Transactional
    public void deleteAutoTrading(
            UUID userId,
            UUID autoTradingId
    ) {
        AutoTrading autoTrading =
                autoTradingCommandRepository
                        .findByIdAndUserIdForUpdate(
                                autoTradingId,
                                userId
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        TradingErrorCode.AUTO_TRADING_NOT_FOUND
                                )
                        );

        if (orderQueryRepository
                .existsActiveOrderByAutoTradingId(autoTradingId)) {
            throw new BusinessException(
                    TradingErrorCode.AUTO_TRADING_HAS_ACTIVE_ORDER
            );
        }

        if (orderQueryRepository
                .existsOpenPositionByAutoTradingId(autoTradingId)) {
            throw new BusinessException(
                    TradingErrorCode.AUTO_TRADING_HAS_OPEN_POSITION
            );
        }

        autoTrading.softDelete(userId);
    }
}