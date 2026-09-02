package com.sajo.user_service.account.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kis.api")
public record KisApiProperties(String virtualBaseUrl, String realBaseUrl) {

    public KisApiProperties {
        if (virtualBaseUrl == null || virtualBaseUrl.isBlank()) {
            virtualBaseUrl = "https://openapivts.koreainvestment.com:29443";
        }
        if (realBaseUrl == null || realBaseUrl.isBlank()) {
            realBaseUrl = "https://openapi.koreainvestment.com:9443";
        }
    }
}
