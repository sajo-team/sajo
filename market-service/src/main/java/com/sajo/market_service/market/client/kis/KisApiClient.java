package com.sajo.market_service.market.client.kis;

import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.config.KisApiProperties;
import com.sajo.market_service.market.dto.kis.KisQuoteResponse;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KisApiClient {

    private static final String INQUIRE_PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String DOMESTIC_STOCK_TRANSACTION_ID = "FHKST01010100";

    private final RestClient restClient;

    public KisApiClient(RestClient.Builder restClientBuilder, KisApiProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    public QuoteResponse getQuote(UserKisTokenResponse credentials, String stockCode) {
        KisQuoteResponse response = restClient.get()
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

        if (response == null) {
            throw new IllegalStateException("KIS 현재가 응답이 비어 있습니다.");
        }
        return QuoteResponse.from(response, stockCode);
    }
}
