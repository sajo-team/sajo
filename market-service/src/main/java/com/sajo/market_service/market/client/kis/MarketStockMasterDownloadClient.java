package com.sajo.market_service.market.client.kis;

import com.sajo.market_service.market.config.MarketStockMasterSyncProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class MarketStockMasterDownloadClient {
    static final String KOSPI_URL = "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip";
    static final String KOSDAQ_URL = "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip";

    private final MarketStockMasterSyncProperties properties;
    private final RestClient restClient;

    @Autowired
    public MarketStockMasterDownloadClient(
            MarketStockMasterSyncProperties properties,
            RestClient.Builder builder
    ) {
        this(properties, builder, true);
    }

    MarketStockMasterDownloadClient(
            MarketStockMasterSyncProperties properties,
            RestClient.Builder builder,
            boolean configureTimeout
    ) {
        this.properties = properties;
        RestClient.Builder clientBuilder = builder.clone();
        if (!configureTimeout) {
            this.restClient = clientBuilder.build();
            return;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.restClient = clientBuilder.requestFactory(factory).build();
    }

    public byte[] downloadKOSPI() {
        return download(KOSPI_URL);
    }

    public byte[] downloadKOSDAQ() {
        return download(KOSDAQ_URL);
    }

    private byte[] download(String url) {
        byte[] body = restClient.get().uri(url).retrieve().body(byte[].class);
        if (body == null || body.length == 0 || body.length > properties.maxDownloadBytes()) {
            throw new IllegalStateException("종목 마스터 다운로드 크기가 유효하지 않습니다.");
        }
        return body;
    }
}
