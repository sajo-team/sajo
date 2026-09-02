package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingCreateRequest;
import com.sajo.trading_service.trading.controller.dto.request.AutoTradingUpdateRequest;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingCreateResponse;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingUpdateResponse;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.AutoTradingCommandRepository;
import com.sajo.trading_service.trading.repository.command.TradingLimitCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutoTradingCommandService {
    private final AutoTradingCommandRepository autoTradingCommandRepository;
    private final TradingLimitCommandRepository tradingLimitCommandRepository;

    @Transactional
    public AutoTradingCreateResponse createAutoTrading(
            UUID userId,
            AutoTradingCreateRequest request
    ){
        if(!tradingLimitCommandRepository.existsByUserId(userId)){
            throw new BusinessException(
                    TradingErrorCode.TRADING_LIMIT_REQUIRED
            );
        }

        // TODO: Strategy 내부 조회 API 구현 후 strategyId 존재 여부 및 사용자 소유 전략인지 검증

        if(autoTradingCommandRepository.existsByUserIdAndStrategyIdAndDeletedAtIsNull(
                userId,
                request.strategyId()
        )){
            throw new BusinessException(
                    TradingErrorCode.AUTO_TRADING_ALREADY_EXISTS
            );
        }
        AutoTrading autoTrading =
                AutoTrading.create(
                        userId,
                        request.strategyId()
                );

        AutoTrading savedAutoTrading =
                autoTradingCommandRepository.save(autoTrading);

        return AutoTradingCreateResponse.from(savedAutoTrading);
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
}
