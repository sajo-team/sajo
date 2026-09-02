package com.sajo.market_service.market.client.kis;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.config.KisApiProperties;
import com.sajo.market_service.market.dto.response.DailyPriceResponse;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class KisApiClientTest {

    private static final UserKisTokenResponse CREDENTIALS =
            new UserKisTokenResponse("access-token", "app-key", "secret-key");
    private static final String DAILY_PRICE_URL =
            "https://kis.example/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";

    @Test
    void callsKisWithUserServiceCredentialsAndMapsRawResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiClient client = new KisApiClient(builder, new KisApiProperties("https://kis.example"));

        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=005930"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("authorization", "Bearer access-token"))
                .andExpect(header("appkey", "app-key"))
                .andExpect(header("appsecret", "secret-key"))
                .andExpect(header("tr_id", "FHKST01010100"))
                .andExpect(queryParam("FID_INPUT_ISCD", "005930"))
                .andRespond(withSuccess("""
                        {"rt_cd":"0","msg_cd":"MCA00000","msg1":"정상처리 되었습니다.","output":{
                          "stck_prpr":"70000","stck_oprc":"69000","stck_hgpr":"70500","stck_lwpr":"68800",
                          "stck_sdpr":"69500","prdy_vrss":"500","prdy_ctrt":"0.7194","acml_vol":"123456",
                          "acml_tr_pbmn":"8610000000","hts_avls":"4180000","per":"15.20","pbr":"1.35",
                          "eps":"4605.00","bps":"51850.00"
                        }}
                        """, MediaType.APPLICATION_JSON));

        QuoteResponse response = client.getQuote(
                new UserKisTokenResponse("access-token", "app-key", "secret-key"),
                "005930"
        );

        assertEquals("005930", response.stockCode());
        assertEquals(70000L, response.currentPrice());
        assertEquals(new BigDecimal("0.7194"), response.changeRate());
        assertEquals(new BigDecimal("15.20"), response.per());
        server.verify();
    }

    @Test
    void throwsExceptionWhenKisBusinessResponseFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiClient client = new KisApiClient(builder, new KisApiProperties("https://kis.example"));

        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=005930"))
                .andRespond(withSuccess("""
                        {"rt_cd":"1","msg_cd":"MCA05918","msg1":"종목코드 오류입니다."}
                        """, MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.getQuote(
                        new UserKisTokenResponse("access-token", "app-key", "secret-key"),
                        "005930"
                )
        );

        assertEquals(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("MCA05918"));
        assertTrue(exception.getMessage().contains("종목코드 오류입니다."));
        server.verify();
    }

    @Test
    void throwsBusinessExceptionWhenKisResponseIsEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiClient client = new KisApiClient(builder, new KisApiProperties("https://kis.example"));

        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=005930"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.getQuote(
                        new UserKisTokenResponse("access-token", "app-key", "secret-key"),
                        "005930"
                )
        );

        assertEquals(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("응답이 비어 있습니다."));
        server.verify();
    }

    @Test
    void convertsKisHttp4xxToBusinessException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiClient client = new KisApiClient(builder, new KisApiProperties("https://kis.example"));

        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=005930"))
                .andRespond(withBadRequest());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.getQuote(
                        new UserKisTokenResponse("access-token", "app-key", "secret-key"),
                        "005930"
                )
        );

        assertEquals(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("httpStatus=400"));
        server.verify();
    }

    @Test
    void convertsKisHttp5xxToBusinessException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiClient client = new KisApiClient(builder, new KisApiProperties("https://kis.example"));

        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=005930"))
                .andRespond(withServerError());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.getQuote(
                        new UserKisTokenResponse("access-token", "app-key", "secret-key"),
                        "005930"
                )
        );

        assertEquals(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("httpStatus=500"));
        server.verify();
    }

    @Test
    void convertsKisConnectionFailureToBusinessException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiClient client = new KisApiClient(builder, new KisApiProperties("https://kis.example"));

        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=005930"))
                .andRespond(request -> {
                    throw new ResourceAccessException("KIS connection timed out");
                });

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.getQuote(
                        new UserKisTokenResponse("access-token", "app-key", "secret-key"),
                        "005930"
                )
        );

        assertEquals(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("KIS 현재가 호출에 실패"));
        server.verify();
    }

    @Test
    void fetchesSingleDailyPricePeriodAndSortsByTradeDate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiClient client = new KisApiClient(builder, new KisApiProperties("https://kis.example"));

        server.expect(requestTo(dailyPriceUrl("20260901", "20260903")))
                .andExpect(header("tr_id", "FHKST03010100"))
                .andRespond(withSuccess(dailyPriceResponse(
                        LocalDate.of(2026, 9, 3),
                        LocalDate.of(2026, 9, 2),
                        LocalDate.of(2026, 9, 1)
                ), MediaType.APPLICATION_JSON));

        List<DailyPriceResponse> prices = client.getDailyPrices(
                CREDENTIALS, "005930", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3));

        assertEquals(List.of(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 3)
        ), prices.stream().map(DailyPriceResponse::tradeDate).toList());
        server.verify();
    }

    @Test
    void splitsMoreThanOneHundredDailyPricesUsingOldestDateAsNextEndDate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiClient client = new KisApiClient(builder, new KisApiProperties("https://kis.example"));
        LocalDate endDate = LocalDate.of(2026, 5, 1);
        List<LocalDate> firstPage = IntStream.range(0, 100)
                .mapToObj(endDate::minusDays)
                .toList();

        server.expect(requestTo(dailyPriceUrl("20260101", "20260501")))
                .andRespond(withSuccess(dailyPriceResponse(firstPage.toArray(LocalDate[]::new)), MediaType.APPLICATION_JSON));
        server.expect(requestTo(dailyPriceUrl("20260101", "20260121")))
                .andRespond(withSuccess(dailyPriceResponse(LocalDate.of(2026, 1, 1)), MediaType.APPLICATION_JSON));

        List<DailyPriceResponse> prices = client.getDailyPrices(
                CREDENTIALS, "005930", LocalDate.of(2026, 1, 1), endDate);

        assertEquals(101, prices.size());
        assertEquals(LocalDate.of(2026, 1, 1), prices.getFirst().tradeDate());
        assertEquals(endDate, prices.getLast().tradeDate());
        server.verify();
    }

    @Test
    void removesDuplicatedTradeDatesAcrossDailyPricePages() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiClient client = new KisApiClient(builder, new KisApiProperties("https://kis.example"));

        server.expect(requestTo(dailyPriceUrl("20260901", "20260903")))
                .andRespond(withSuccess(dailyPriceResponse(
                        LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 2)), MediaType.APPLICATION_JSON));
        server.expect(requestTo(dailyPriceUrl("20260901", "20260901")))
                .andRespond(withSuccess(dailyPriceResponse(
                        LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)), MediaType.APPLICATION_JSON));

        List<DailyPriceResponse> prices = client.getDailyPrices(
                CREDENTIALS, "005930", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3));

        assertEquals(List.of(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 3)
        ), prices.stream().map(DailyPriceResponse::tradeDate).toList());
        server.verify();
    }

    @Test
    void stopsWhenDailyPriceResponseDoesNotMoveToAnOlderDate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiClient client = new KisApiClient(builder, new KisApiProperties("https://kis.example"));

        server.expect(requestTo(dailyPriceUrl("20260901", "20260903")))
                .andRespond(withSuccess(dailyPriceResponse(LocalDate.of(2026, 9, 3)), MediaType.APPLICATION_JSON));

        List<DailyPriceResponse> prices = client.getDailyPrices(
                CREDENTIALS, "005930", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3));

        assertEquals(1, prices.size());
        assertEquals(LocalDate.of(2026, 9, 3), prices.getFirst().tradeDate());
        server.verify();
    }

    @Test
    void throwsBusinessExceptionWhenDailyPriceBusinessResponseFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiClient client = new KisApiClient(builder, new KisApiProperties("https://kis.example"));

        server.expect(requestTo(dailyPriceUrl("20260901", "20260901")))
                .andRespond(withSuccess("""
                        {"rt_cd":"1","msg_cd":"MCA05918","msg1":"종목코드 오류입니다."}
                        """, MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(BusinessException.class, () -> client.getDailyPrices(
                CREDENTIALS, "005930", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)));

        assertEquals(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("MCA05918"));
        server.verify();
    }

    @Test
    void convertsDailyPriceHttpFailureToBusinessException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiClient client = new KisApiClient(builder, new KisApiProperties("https://kis.example"));

        server.expect(requestTo(dailyPriceUrl("20260901", "20260901"))).andRespond(withServerError());

        BusinessException exception = assertThrows(BusinessException.class, () -> client.getDailyPrices(
                CREDENTIALS, "005930", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)));

        assertEquals(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, exception.getErrorCode());
        server.verify();
    }

    @Test
    void convertsDailyPriceConnectionFailureToBusinessException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KisApiClient client = new KisApiClient(builder, new KisApiProperties("https://kis.example"));

        server.expect(requestTo(dailyPriceUrl("20260901", "20260901"))).andRespond(request -> {
            throw new ResourceAccessException("KIS connection timed out");
        });

        BusinessException exception = assertThrows(BusinessException.class, () -> client.getDailyPrices(
                CREDENTIALS, "005930", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)));

        assertEquals(MarketErrorCode.KIS_QUOTE_RESPONSE_INVALID, exception.getErrorCode());
        server.verify();
    }

    private static String dailyPriceUrl(String startDate, String endDate) {
        return DAILY_PRICE_URL + "?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=005930"
                + "&FID_INPUT_DATE_1=" + startDate + "&FID_INPUT_DATE_2=" + endDate
                + "&FID_PERIOD_DIV_CODE=D&FID_ORG_ADJ_PRC=0";
    }

    private static String dailyPriceResponse(LocalDate... dates) {
        String output = java.util.Arrays.stream(dates)
                .map(date -> """
                        {"stck_bsop_date":"%s","stck_oprc":"69000","stck_hgpr":"70500","stck_lwpr":"68800","stck_clpr":"70000","acml_vol":"123456","acml_tr_pbmn":"8610000000"}
                        """.formatted(date.format(DateTimeFormatter.BASIC_ISO_DATE)))
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"rt_cd\":\"0\",\"msg_cd\":\"MCA00000\",\"msg1\":\"정상처리 되었습니다.\",\"output2\":[" + output + "]}";
    }
}
