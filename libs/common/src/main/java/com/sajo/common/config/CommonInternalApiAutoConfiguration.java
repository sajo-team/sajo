package com.sajo.common.config;

import com.sajo.common.security.InternalApiAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
public class CommonInternalApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(InternalApiAuthenticationFilter.class)
    public InternalApiAuthenticationFilter internalApiAuthenticationFilter(
            @Value("${sajo.internal.api-secret:}") String internalApiSecret
    ) {
        return new InternalApiAuthenticationFilter(internalApiSecret);
    }

    // Spring Security의 SecurityFilterChain(permitAll)보다 먼저 실행되어야 하므로
    // SecurityFilterChain에 addFilterBefore로 끼워 넣지 않고, 서블릿 컨테이너 레벨의
    // 별도 Filter로 등록해 /internal/v1/*에만 최우선 순위로 적용한다.
    @Bean
    public FilterRegistrationBean<InternalApiAuthenticationFilter> internalApiAuthenticationFilterRegistration(
            InternalApiAuthenticationFilter internalApiAuthenticationFilter
    ) {
        FilterRegistrationBean<InternalApiAuthenticationFilter> registration =
                new FilterRegistrationBean<>(internalApiAuthenticationFilter);
        registration.addUrlPatterns("/internal/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
