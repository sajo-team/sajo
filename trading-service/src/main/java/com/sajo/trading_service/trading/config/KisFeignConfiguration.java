package com.sajo.trading_service.trading.config;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class KisFeignConfiguration {

    @Bean
    public ErrorDecoder kisErrorDecoder() {
        return new ErrorDecoder.Default();
    }
}