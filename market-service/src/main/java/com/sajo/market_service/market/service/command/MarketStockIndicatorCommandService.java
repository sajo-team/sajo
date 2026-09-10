package com.sajo.market_service.market.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.client.kis.KisApiClient;
import com.sajo.market_service.market.client.user.UserAccountFeignClient;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.dto.command.MarketStockIndicatorCommand;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.dto.response.FinancialRatioResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * KIS 현재가에서 투자지표를 가져와 유효한 데이터만 Persistence Service에 저장 요청하는 클래스
 * KIS에서 투자지표를 가져온 다음 DB 저장 담당자에게 넘기는 중간 관리자
 *
 * 이 클래스는 외부 API 호출 흐름만 조정하고, DB 트랜잭션은 Persistence Service가 담당한다.
 * */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketStockIndicatorCommandService {

    private final UserAccountFeignClient userAccountFeignClient;
    private final KisApiClient kisApiClient;
    private final MarketStockIndicatorPersistenceService marketStockIndicatorPersistenceService;

    /**
     * 시스템 사용자 UUID로 User Service에서 KIS 인증정보를 가져온다.
     *
     * @param userId
     * @return
     */
    public UserKisTokenResponse getCollectionCredentials(UUID userId) {
        return userAccountFeignClient.getKisToken(userId);
    }

    /**
     * 이미 준비된 인증정보와 종목 ID로 KIS를 호출하고 투자지표를 저장한다.
     *
     * @param credentials KIS 인증정보
     * @param stockId DB 내부 종목 UUID
     * @param stockCode KIS에서 사용하는 종목코드
     * @return
     */
    public IndicatorCollectionResult collectAndSaveIndicatorsForIdentifiedStock(
            UserKisTokenResponse credentials,
            UUID stockId,
            String stockCode,
            BooleanSupplier beforeKisRequest
    ) {
        if (stockId == null) {
            throw new BusinessException(MarketErrorCode.INVALID_MARKET_STOCK_INDICATOR, "종목 식별자가 올바르지 않습니다.");
        }

        if (!beforeKisRequest.getAsBoolean()) {
            return IndicatorCollectionResult.INTERRUPTED;
        }
        QuoteResponse quote = kisApiClient.getQuote(credentials, stockCode);
        if (quote == null) {
            throw new BusinessException(
                    MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                    "KIS 현재가 응답이 비어 있습니다."
            );
        }
        if (!beforeKisRequest.getAsBoolean()) {
            return IndicatorCollectionResult.INTERRUPTED;
        }
        Optional<FinancialRatioResponse> financialRatio =
                kisApiClient.getLatestQuarterlyFinancialRatio(credentials, stockCode);
        Optional<MarketStockIndicatorCommand> indicator = financialRatio
                .flatMap(financial -> MarketStockIndicatorCommand.from(quote, financial));
        if (indicator.isEmpty()) {
            log.warn("KIS 투자지표 스냅샷 저장을 건너뜁니다. stockCode={}, reason={}",
                    stockCode, "missingRequiredValuationOrFinancialData");
            return IndicatorCollectionResult.SKIPPED;
        }
        marketStockIndicatorPersistenceService.save(stockId, indicator.get());
        return IndicatorCollectionResult.SAVED;
    }

    public enum IndicatorCollectionResult { SAVED, SKIPPED, INTERRUPTED }
}
