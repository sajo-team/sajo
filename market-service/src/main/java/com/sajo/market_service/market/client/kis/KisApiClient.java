package com.sajo.market_service.market.client.kis;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.config.KisApiProperties;
import com.sajo.market_service.market.dto.kis.KisQuoteResponse;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
public class KisApiClient {

    private static final String INQUIRE_PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String DOMESTIC_STOCK_TRANSACTION_ID = "FHKST01010100";

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
}
