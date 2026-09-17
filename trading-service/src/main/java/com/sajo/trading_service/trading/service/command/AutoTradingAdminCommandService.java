package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.AutoTradingCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutoTradingAdminCommandService {

    private final AutoTradingCommandRepository autoTradingCommandRepository;

    @Transactional
    public void suspend(UUID autoTradingId) {
        AutoTrading autoTrading =
                autoTradingCommandRepository
                        .findByIdForUpdate(autoTradingId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        TradingErrorCode.AUTO_TRADING_NOT_FOUND
                                )
                        );

        autoTrading.suspendByAdmin();
    }

    @Transactional
    public void resume(UUID autoTradingId) {
        AutoTrading autoTrading =
                autoTradingCommandRepository
                        .findByIdForUpdate(autoTradingId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        TradingErrorCode.AUTO_TRADING_NOT_FOUND
                                )
                        );

        autoTrading.resumeByAdmin();
    }
}