package com.sajo.common.config;

import com.sajo.common.observation.QueryStrippingClientRequestObservationConvention;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.observation.ClientRequestObservationConvention;

@AutoConfiguration
public class CommonHttpClientObservationAutoConfiguration {

    // Boot의 RestClient 계측(ObservationRestClientCustomizer)은 이 타입의 빈이 있으면 기본 convention 대신 사용한다.
    // 이유는 QueryStrippingClientRequestObservationConvention 주석 참고
    @Bean
    @ConditionalOnMissingBean(ClientRequestObservationConvention.class)
    public ClientRequestObservationConvention queryStrippingClientRequestObservationConvention() {
        return new QueryStrippingClientRequestObservationConvention();
    }
}
