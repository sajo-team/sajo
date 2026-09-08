package com.sajo.common.config;

import feign.Logger;
import com.sajo.common.feign.CommonFeignErrorDecoder;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
public class CommonFeignAutoConfiguration {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    @Bean
    public RequestInterceptor userHeaderRequestInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes == null) {
                return;
            }

            HttpServletRequest request = attributes.getRequest();

            String userId = request.getHeader(USER_ID_HEADER);
            String userRole = request.getHeader(USER_ROLE_HEADER);

            if (userId != null) {
                requestTemplate.header(USER_ID_HEADER, userId);
            }

            if (userRole != null) {
                requestTemplate.header(USER_ROLE_HEADER, userRole);
            }
        };
    }

    // userHeaderRequestInterceptor와 달리 RequestContextHolder(현재 처리 중인 HTTP 요청)에 의존하지
    // 않는다 - Kafka 컨슈머 스레드 등 요청 컨텍스트가 없는 곳에서 나가는 Feign 호출에도 항상 붙어야
    // InternalApiAuthenticationFilter를 통과할 수 있기 때문이다.
    @Bean
    public RequestInterceptor internalApiSecretRequestInterceptor(
            @Value("${sajo.internal.api-secret:}") String internalApiSecret
    ) {
        return requestTemplate -> {
            if (internalApiSecret != null && !internalApiSecret.isBlank()) {
                requestTemplate.header(INTERNAL_SECRET_HEADER, internalApiSecret);
            }
        };
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    public ErrorDecoder commonFeignErrorDecoder(ObjectMapper objectMapper) {
        return new CommonFeignErrorDecoder(objectMapper);
    }

}
