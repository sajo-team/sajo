package com.sajo.market_service.market.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(MarketSchedulerProperties.class)
public class MarketSchedulerConfiguration {

    @Bean
    Clock marketSchedulerClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
