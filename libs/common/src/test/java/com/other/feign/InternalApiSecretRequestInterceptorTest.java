package com.other.feign;

import com.sajo.common.config.CommonFeignAutoConfiguration;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// PR 리뷰(Critical)에서 지적된 문제 재현/회귀 방지용 테스트: internalApiSecretRequestInterceptor가
// 전역 Bean이라 KisOrderClient 같은 실제 외부 API 호출에도 시크릿 헤더가 실릴 뻔했다.
@DisplayName("internalApiSecretRequestInterceptor 테스트")
class InternalApiSecretRequestInterceptorTest {

    private static final String SECRET = "test-internal-api-secret";

    private final RequestInterceptor interceptor =
            new CommonFeignAutoConfiguration().internalApiSecretRequestInterceptor(SECRET);

    @Test
    @DisplayName("/internal/v1/** 대상 요청에는 시크릿 헤더를 붙인다")
    void attachesSecretForInternalApiCall() {
        RequestTemplate template = new RequestTemplate();
        template.uri("/internal/v1/trading/users/00000000-0000-0000-0000-000000000000/active-status");

        interceptor.apply(template);

        assertThat(template.headers().get("X-Internal-Secret")).containsExactly(SECRET);
    }

    @Test
    @DisplayName("/internal/v1/**가 아닌 대상(KIS 외부 API 등)에는 시크릿 헤더를 절대 붙이지 않는다")
    void neverAttachesSecretForExternalCall() {
        RequestTemplate template = new RequestTemplate();
        template.uri("/uapi/domestic-stock/v1/trading/order-cash");

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("X-Internal-Secret");
    }

    @Test
    @DisplayName("시크릿 설정값이 비어있으면 내부 API 대상이어도 헤더를 붙이지 않는다")
    void blankSecret_addsNoHeaderEvenForInternalPath() {
        RequestInterceptor blankSecretInterceptor =
                new CommonFeignAutoConfiguration().internalApiSecretRequestInterceptor("");
        RequestTemplate template = new RequestTemplate();
        template.uri("/internal/v1/users/00000000-0000-0000-0000-000000000000");

        blankSecretInterceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("X-Internal-Secret");
    }
}
