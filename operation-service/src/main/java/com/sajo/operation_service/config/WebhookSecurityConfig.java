package com.sajo.operation_service.config;

import com.sajo.operation_service.security.AlertWebhookAuthenticationFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Configuration
public class WebhookSecurityConfig {

    @Bean
    public AlertWebhookAuthenticationFilter alertWebhookAuthenticationFilter(
            @Value("${sajo.webhook.secret-file-path:}") String secretFilePath
    ) {
        return new AlertWebhookAuthenticationFilter(readSecret(secretFilePath));
    }

    // Spring Security가 없는 서비스라 SecurityFilterChain에 끼워 넣지 않고, 순수 서블릿 Filter로
    // /api/v1/operations/webhook에만 최우선 순위로 등록한다
    @Bean
    public FilterRegistrationBean<AlertWebhookAuthenticationFilter> alertWebhookAuthenticationFilterRegistration(
            AlertWebhookAuthenticationFilter alertWebhookAuthenticationFilter
    ) {
        FilterRegistrationBean<AlertWebhookAuthenticationFilter> registration =
                new FilterRegistrationBean<>(alertWebhookAuthenticationFilter);
        registration.addUrlPatterns("/api/v1/operations/webhook");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    private String readSecret(String secretFilePath) {
        if (secretFilePath == null || secretFilePath.isBlank()) {
            return null;
        }
        try {
            return Files.readString(Path.of(secretFilePath)).trim();
        } catch (IOException e) {
            log.error("웹훅 시크릿 파일을 읽을 수 없습니다. path={}", secretFilePath, e);
            return null;
        }
    }
}
