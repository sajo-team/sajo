package com.sajo.common.config;

import feign.Logger;
import com.sajo.common.feign.CommonFeignErrorDecoder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
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
    private static final String INTERNAL_API_PATH_PREFIX = "/internal/v1/";

    @Bean
    public RequestInterceptor userHeaderRequestInterceptor() {
        return requestTemplate -> {
            if (!isInternalApiCall(requestTemplate)) {
                return;
            }

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
    //
    // isInternalApiCall 가드가 반드시 필요하다 - 이 Bean은 서비스 내 모든 FeignClient에 전역
    // 적용되는데, trading-service의 KisOrderClient처럼 실제 외부 API(KIS)를 호출하는 클라이언트도
    // 예외가 아니다. 가드 없이는 서비스 간 인증 전용 시크릿이 그대로 외부 서버로 전송된다.
    @Bean
    public RequestInterceptor internalApiSecretRequestInterceptor(
            @Value("${sajo.internal.api-secret:}") String internalApiSecret
    ) {
        return requestTemplate -> {
            if (!isInternalApiCall(requestTemplate)) {
                return;
            }

            if (internalApiSecret != null && !internalApiSecret.isBlank()) {
                requestTemplate.header(INTERNAL_SECRET_HEADER, internalApiSecret);
            }
        };
    }

    // CLAUDE.md 8번 규칙("내부 서비스 API는 /internal/v1/** 규칙을 따른다")을 그대로 판별 기준으로
    // 쓴다. 인터셉터 실행 순서에 기대지 않고, 애초에 이 경로가 아니면 헤더를 붙이지 않는 방식이라
    // KisFeignConfiguration의 명시적 제거 인터셉터와 별개로 안전하다(이중 방어).
    private boolean isInternalApiCall(RequestTemplate requestTemplate) {
        String path = requestTemplate.path();
        return path != null && path.startsWith(INTERNAL_API_PATH_PREFIX);
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
