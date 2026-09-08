package com.sajo.trading_service.trading.config;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class KisFeignConfiguration {

    @Bean
    public ErrorDecoder kisErrorDecoder() {
        return new ErrorDecoder.Default();
    }

    // CommonFeignAutoConfiguration의 internalApiSecretRequestInterceptor/userHeaderRequestInterceptor는
    // 전역 @Bean이라 서비스 내 모든 FeignClient(이 KIS 외부 API 클라이언트 포함)에 자동 적용된다.
    // KisOrderClient는 실제 외부 KIS 브로커리지 서버를 직접 호출하므로(url = ${kis.base-url}),
    // 내부 서비스 간 인증에만 쓰여야 하는 시크릿/사용자 식별 헤더가 그대로 외부로 나가면 안 된다.
    // 같은 Feign 컨텍스트 안에서는 여러 RequestInterceptor가 등록 순서대로 순차 적용되므로,
    // 이 인터셉터가 나중에 실행되어 최종적으로 헤더를 제거하기만 하면 값이 밖으로 나가지 않는다.
    @Bean
    public RequestInterceptor kisExternalHeaderScrubber() {
        return requestTemplate -> {
            requestTemplate.removeHeader("X-Internal-Secret");
            requestTemplate.removeHeader("X-User-Id");
            requestTemplate.removeHeader("X-User-Role");
        };
    }
}