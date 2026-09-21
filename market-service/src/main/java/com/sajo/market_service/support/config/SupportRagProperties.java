package com.sajo.market_service.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sajo.support.rag")
public record SupportRagProperties(
        Boolean enabled,
        String documentPath,
        Integer topK
) {

    public SupportRagProperties {
        enabled = enabled != null && enabled;
        topK = (topK == null || topK <= 0) ? 3 : topK;
    }
}
