package com.sajo.market_service.market.client.kis;

import com.sajo.common.exception.BusinessException;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import com.sajo.market_service.market.config.KisApiProperties;
import com.sajo.market_service.market.dto.response.QuoteResponse;
import com.sajo.market_service.market.exception.MarketErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisApiClientTest {

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
}
