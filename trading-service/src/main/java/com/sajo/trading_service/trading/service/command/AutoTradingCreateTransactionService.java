package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingCreateRequest;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingCreateResponse;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.AutoTradingCommandRepository;
import com.sajo.trading_service.trading.repository.command.TradingLimitCommandRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutoTradingCreateTransactionService {

    private static final String AUTO_TRADING_UNIQUE_INDEX =
            "uq_auto_trading_active_user_strategy";

    private final AutoTradingCommandRepository autoTradingCommandRepository;
    private final TradingLimitCommandRepository tradingLimitCommandRepository;

    @Transactional
    public AutoTradingCreateResponse create(
            UUID userId,
            AutoTradingCreateRequest request
    ) {
        if (!tradingLimitCommandRepository.existsByUserId(userId)) {
            throw new BusinessException(
                    TradingErrorCode.TRADING_LIMIT_REQUIRED
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