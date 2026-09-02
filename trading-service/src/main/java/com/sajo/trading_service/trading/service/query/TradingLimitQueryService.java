package com.sajo.trading_service.trading.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.response.TradingLimitQueryResponse;
import com.sajo.trading_service.trading.domain.TradingLimit;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.query.TradingLimitQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradingLimitQueryService {
    private final TradingLimitQueryRepository tradingLimitQueryRepository;

    public TradingLimitQueryResponse findByUserId(UUID userId){
        TradingLimit tradingLimit =
                tradingLimitQueryRepository.findByUserId(userId)
                        .orElseThrow(()->
                                new BusinessException(
                                        TradingErrorCode.TRADING_LIMIT_NOT_FOUND
                                )
                        );
        return TradingLimitQueryResponse.from(tradingLimit);
    }
}
