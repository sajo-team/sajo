package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingUpdateRequest;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingUpdateResponse;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.AutoTradingCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutoTradingUpdateTransactionService {

    private final AutoTradingCommandRepository autoTradingCommandRepository;

    @Transactional
    public AutoTradingUpdateResponse update(
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

        autoTrading.update(
                request.enabled(),
                request.direction()
        );

        return AutoTradingUpdateResponse.from(autoTrading);
    }
}