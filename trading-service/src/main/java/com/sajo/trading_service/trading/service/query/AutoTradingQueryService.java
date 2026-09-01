package com.sajo.trading_service.trading.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.trading.controller.dto.response.AutoTradingQueryResponse;
import com.sajo.trading_service.trading.domain.AutoTrading;
import com.sajo.trading_service.trading.exception.TradingErrorCode;
import com.sajo.trading_service.trading.repository.query.AutoTradingQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutoTradingQueryService {
    private final AutoTradingQueryRepository autoTradingQueryRepository;

    public Page<AutoTradingQueryResponse> findAllByUserId(
            UUID userId,
            Pageable pageable
    ) {
        return autoTradingQueryRepository
                .findAllByUserIdAndDeletedAtIsNull(userId, pageable)
                .map(AutoTradingQueryResponse::from);
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
        return AutoTradingQueryResponse.from(autoTrading);
    }
}
