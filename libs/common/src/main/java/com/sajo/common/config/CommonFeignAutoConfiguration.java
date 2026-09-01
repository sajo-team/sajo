package com.sajo.common.config;

import feign.Logger;
import com.sajo.common.feign.CommonFeignErrorDecoder;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
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
