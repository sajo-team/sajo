package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
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
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutoTradingCommandService {
    private static final String AUTO_TRADING_UNIQUE_INDEX =
            "uq_auto_trading_active_user_strategy";

    private final AutoTradingCommandRepository autoTradingCommandRepository;
    private final TradingLimitCommandRepository tradingLimitCommandRepository;
    private final StrategyClient strategyClient;

    @Transactional
    public AutoTradingCreateResponse createAutoTrading(
            UUID userId,
            AutoTradingCreateRequest request
    ){
        if (!tradingLimitCommandRepository.existsByUserId(userId)) {
            throw new BusinessException(
                    TradingErrorCode.TRADING_LIMIT_REQUIRED
            );
        }

        StrategyClientResponse strategy;

        try {
            strategy = strategyClient.getStrategy(request.strategyId());
        } catch (FeignException.NotFound e) {
            throw new BusinessException(
                    TradingErrorCode.STRATEGY_NOT_FOUND
            );
        }

        if (!strategy.userId().equals(userId)) {
            throw new BusinessException(
                    TradingErrorCode.STRATEGY_NOT_FOUND
            );
        }

        if (autoTradingCommandRepository
                .existsByUserIdAndStrategyIdAndDeletedAtIsNull(
                        userId,
                        request.strategyId()
                )) {
            throw new BusinessException(
                    TradingErrorCode.AUTO_TRADING_ALREADY_EXISTS
            );
        }

        AutoTrading autoTrading =
                AutoTrading.create(
                        userId,
                        request.strategyId()
                );

        try {
            AutoTrading savedAutoTrading =
                    autoTradingCommandRepository.saveAndFlush(autoTrading);

            return AutoTradingCreateResponse.from(savedAutoTrading);

        } catch (DataIntegrityViolationException e) {
            if (isAutoTradingUniqueViolation(e)) {
                throw new BusinessException(
                        TradingErrorCode.AUTO_TRADING_ALREADY_EXISTS
                );
            }

            throw e;
        }
    }

    @Transactional
    public AutoTradingUpdateResponse updateAutoTrading(
            UUID userId,
            UUID autoTradingId,
            AutoTradingUpdateRequest request
    ){
        AutoTrading autoTrading =
                autoTradingCommandRepository
                        .findByIdAndUserIdAndDeletedAtIsNull(
                            autoTradingId,
                            userId
                        )
                        .orElseThrow(()->
                                new BusinessException(
                                        TradingErrorCode.AUTO_TRADING_NOT_FOUND)
                        );
        if(Boolean.TRUE.equals(request.enabled())
            && !tradingLimitCommandRepository.existsByUserId(userId)){
            throw new BusinessException(
                    TradingErrorCode.TRADING_LIMIT_REQUIRED);
        }

        autoTrading.update(request.enabled());

        return AutoTradingUpdateResponse.from(autoTrading);
    }

    private boolean isAutoTradingUniqueViolation(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException) {
                return AUTO_TRADING_UNIQUE_INDEX.equals(
                        constraintViolationException.getConstraintName()
                );
            }

            cause = cause.getCause();
        }

        return false;
    }
}
