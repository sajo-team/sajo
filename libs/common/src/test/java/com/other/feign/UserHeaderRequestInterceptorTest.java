package com.other.feign;

import com.sajo.common.config.CommonFeignAutoConfiguration;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Feign 헤더 전파 인터셉터 테스트")
class UserHeaderRequestInterceptorTest {

    private final RequestInterceptor interceptor = new CommonFeignAutoConfiguration().userHeaderRequestInterceptor();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("원 요청의 X-User-Id/X-User-Role을 /internal/v1/** 대상 Feign 요청에 그대로 실어보낸다")
    void propagatesHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "user-1");
        request.addHeader("X-User-Role", "ADMIN");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        template.uri("/internal/v1/accounts/00000000-0000-0000-0000-000000000000/token");
        interceptor.apply(template);

        assertThat(template.headers().get("X-User-Id")).containsExactly("user-1");
        assertThat(template.headers().get("X-User-Role")).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("/internal/v1/**가 아닌 대상(예: KIS 외부 API)에는 헤더를 붙이지 않는다")
    void nonInternalPath_addsNoHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "user-1");
        request.addHeader("X-User-Role", "ADMIN");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        template.uri("/uapi/domestic-stock/v1/trading/order-cash");
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKeys("X-User-Id", "X-User-Role");
    }

    @Test
    @DisplayName("원 요청 컨텍스트가 없으면(배치 등) 아무 헤더도 안 붙인다")
    void noRequestContext_addsNoHeaders() {
        RequestTemplate template = new RequestTemplate();
        template.uri("/internal/v1/accounts/00000000-0000-0000-0000-000000000000/token");
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKeys("X-User-Id", "X-User-Role");
    }
}
