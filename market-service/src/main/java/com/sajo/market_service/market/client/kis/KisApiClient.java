package com.sajo.market_service.market.client.kis;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.config.KisApiProperties;
import com.sajo.market_service.market.dto.kis.KisQuoteResponse;
import com.sajo.market_service.market.dto.kis.KisDailyPriceResponse;
import com.sajo.market_service.market.dto.response.DailyPriceResponse;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

@Component
@Slf4j
public class KisApiClient {

    private static final String INQUIRE_PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String DOMESTIC_STOCK_TRANSACTION_ID = "FHKST01010100";
    private static final String INQUIRE_DAILY_PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";
    private static final String DAILY_PRICE_TRANSACTION_ID = "FHKST03010100";
    private static final long MAX_DAILY_PRICE_LOOKBACK_DAYS = 365;

    private final RestClient restClient;

    public KisApiClient(RestClient.Builder restClientBuilder, KisApiProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    public QuoteResponse getQuote(UserKisTokenResponse credentials, String stockCode) {
        KisQuoteResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(INQUIRE_PRICE_PATH)
                            .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                            .queryParam("FID_INPUT_ISCD", stockCode)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .header("authorization", "Bearer " + credentials.accessToken())
                    .header("appkey", credentials.appKey())
                    .header("appsecret", credentials.secretKey())
                    .header("tr_id", DOMESTIC_STOCK_TRANSACTION_ID)
                    .header("custtype", "P")
                    .retrieve()
                    .body(KisQuoteResponse.class);
        } catch (RestClientResponseException exception) {
            log.warn("KIS 현재가 HTTP 호출 실패. stockCode={}, status={}",
                    stockCode, exception.getStatusCode(), exception);
            throw new BusinessException(
                    MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                    "KIS 현재가 호출에 실패했습니다. httpStatus=%s"
                            .formatted(exception.getStatusCode().value())
            );
        } catch (RestClientException exception) {
            log.warn("KIS 현재가 연결 호출 실패. stockCode={}, exceptionType={}",
                    stockCode, exception.getClass().getSimpleName(), exception);
            throw new BusinessException(
                    MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                    "KIS 현재가 호출에 실패했습니다."
            );
        }

        if (response == null) {
            throw new BusinessException(
                    MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                    "KIS 현재가 응답이 비어 있습니다."
            );
        }
        if (!response.isSuccess()) {
            throw new BusinessException(
                    MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID,
                    "KIS 현재가 조회에 실패했습니다. msg_cd=%s, msg1=%s"
                            .formatted(response.messageCode(), response.message())
            );
        }
        return QuoteResponse.from(response, stockCode);
    }

    public List<DailyPriceResponse> getDailyPrices(UserKisTokenResponse credentials, String stockCode,
                                                    LocalDate startDate, LocalDate endDate) {
        validateDailyPricePeriod(startDate, endDate);
        List<DailyPriceResponse> prices = new ArrayList<>();
        Set<LocalDate> seenDates = new HashSet<>();
        LocalDate currentEndDate = endDate;
        while (!currentEndDate.isBefore(startDate)) {
            List<DailyPriceResponse> page = getDailyPricePage(credentials, stockCode, startDate, currentEndDate);
            if (page.isEmpty()) break;
            page.stream().filter(price -> !price.tradeDate().isBefore(startDate) && !price.tradeDate().isAfter(endDate))
                    .filter(price -> seenDates.add(price.tradeDate())).forEach(prices::add);
            LocalDate oldestDate = page.stream().map(DailyPriceResponse::tradeDate).min(Comparator.naturalOrder()).orElse(null);
            if (oldestDate == null || !oldestDate.isBefore(currentEndDate) || oldestDate.isEqual(startDate)) break;
            currentEndDate = oldestDate.minusDays(1);
        }
        return prices.stream().sorted(Comparator.comparing(DailyPriceResponse::tradeDate)).toList();
    }

    private void validateDailyPricePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)
                || ChronoUnit.DAYS.between(startDate, endDate) > MAX_DAILY_PRICE_LOOKBACK_DAYS) {
            throw new BusinessException(
                    MarketErrorCode.INVALID_MARKET_STOCK_PRICE,
                    "일별 시세 조회 기간은 최대 %d일입니다.".formatted(MAX_DAILY_PRICE_LOOKBACK_DAYS)
            );
        }
    }

    private List<DailyPriceResponse> getDailyPricePage(UserKisTokenResponse credentials, String stockCode,
                                                        LocalDate startDate, LocalDate endDate) {
        KisDailyPriceResponse response;
        try {
            response = restClient.get().uri(uriBuilder -> uriBuilder.path(INQUIRE_DAILY_PRICE_PATH)
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J").queryParam("FID_INPUT_ISCD", stockCode)
                    .queryParam("FID_INPUT_DATE_1", startDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                    .queryParam("FID_INPUT_DATE_2", endDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                    .queryParam("FID_PERIOD_DIV_CODE", "D").queryParam("FID_ORG_ADJ_PRC", "0").build())
                    .accept(MediaType.APPLICATION_JSON).header("authorization", "Bearer " + credentials.accessToken())
                    .header("appkey", credentials.appKey()).header("appsecret", credentials.secretKey())
                    .header("tr_id", DAILY_PRICE_TRANSACTION_ID).header("custtype", "P").retrieve()
                    .body(KisDailyPriceResponse.class);
        } catch (RestClientException exception) {
            log.warn("KIS 일별 시세 호출 실패. stockCode={}, exceptionType={}", stockCode, exception.getClass().getSimpleName(), exception);
            throw new BusinessException(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, "KIS 일별 시세 호출에 실패했습니다.");
        }
        if (response == null || !response.isSuccess()) {
            String message = response == null ? "응답이 비어 있습니다." : "msg_cd=%s, msg1=%s".formatted(response.messageCode(), response.message());
            throw new BusinessException(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, "KIS 일별 시세 조회에 실패했습니다. " + message);
        }
        return response.output2() == null ? List.of() : response.output2().stream()
                .map(output -> DailyPriceResponse.from(output, stockCode))
                .flatMap(java.util.Optional::stream)
                .toList();
    }
}
