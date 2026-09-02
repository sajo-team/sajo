package com.sajo.user_service.account.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kis.api")
public record KisApiProperties(String baseUrl) {

    public KisApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://openapivts.koreainvestment.com:29443";
        }
    }
}
