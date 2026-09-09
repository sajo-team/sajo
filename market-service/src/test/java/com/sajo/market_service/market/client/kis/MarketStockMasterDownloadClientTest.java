package com.sajo.market_service.market.client.kis;

import com.sajo.market_service.market.config.MarketStockMasterSyncProperties;
import com.sajo.market_service.market.config.KisApiProperties;
import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MarketStockMasterDownloadClientTest {
    private MockRestServiceServer server;
    private MarketStockMasterDownloadClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MarketStockMasterDownloadClient(
                new MarketStockMasterSyncProperties(true, 10, 4, Duration.ofSeconds(3), Duration.ofSeconds(5)),
                builder, false);
    }

    @Test
    void rejectsNon2xxResponse() {
        server.expect(requestTo(MarketStockMasterDownloadClient.KOSPI_URL))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(client::downloadKOSPI).isInstanceOf(RuntimeException.class);
        server.verify();
    }

    @Test
    void appliesActualBodySizeLimitEvenWithoutContentLength() {
        server.expect(requestTo(MarketStockMasterDownloadClient.KOSPI_URL))
                .andRespond(withSuccess(new byte[]{1, 2, 3, 4, 5}, MediaType.APPLICATION_OCTET_STREAM));

        assertThatThrownBy(client::downloadKOSPI).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void clientsUsingSameBuilderKeepTheirOwnConfiguration() {
        RestClient.Builder sharedBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(sharedBuilder).build();
        client = new MarketStockMasterDownloadClient(
                new MarketStockMasterSyncProperties(true, 10, 100, Duration.ofSeconds(3), Duration.ofSeconds(5)),
                sharedBuilder, false);
        KisApiClient kisClient = new KisApiClient(sharedBuilder, new KisApiProperties("https://kis.example"));

        server.expect(requestTo(MarketStockMasterDownloadClient.KOSPI_URL))
                .andRespond(withSuccess(new byte[]{1}, MediaType.APPLICATION_OCTET_STREAM));
        server.expect(requestTo("https://kis.example/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=005930"))
                .andRespond(withSuccess(
                        "{\"rt_cd\":\"0\",\"msg_cd\":\"MCA00000\",\"msg1\":\"ok\","
                                + "\"output\":{\"stck_prpr\":\"70000\"}}",
                        MediaType.APPLICATION_JSON));

        client.downloadKOSPI();
        kisClient.getQuote(new UserKisTokenResponse("token", "key", "secret"), "005930");

        server.verify();
    }
}
