package com.sajo.trading_service.trading.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.request.TradingLimitCreateRequest;
import com.sajo.trading_service.trading.controller.dto.response.TradingLimitCreateResponse;
import com.sajo.trading_service.trading.domain.TradingLimit;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.command.TradingLimitCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TradingLimitCommandService {
    private final TradingLimitCommandRepository tradingLimitCommandRepository;

    @Transactional
    public TradingLimitCreateResponse createTradingLimit(
            UUID userId,
            TradingLimitCreateRequest request
    ){
        if(tradingLimitCommandRepository.existsByUserId(userId)){
            throw new BusinessException(
                    TradingErrorCode.TRADING_LIMIT_ALREADY_EXISTS,
                    "공통 한도 설정이 이미 존재합니다."
            );
        }

        TradingLimit tradingLimit = TradingLimit.create(
                userId,
                request.dailyMaxOrderAmount(),
                request.dailyMaxOrderCount(),
                request.dailyLossLimitRate()
        );

        TradingLimit savedTradingLimit = tradingLimitCommandRepository.save(tradingLimit);

        return TradingLimitCreateResponse.from(savedTradingLimit);
    }
}
