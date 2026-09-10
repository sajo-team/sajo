package com.sajo.market_service.market.client;

import com.sajo.common.config.CommonFeignAutoConfiguration;
import com.sajo.market_service.market.client.user.UserAccountFeignClient;
import com.sajo.market_service.strategy.client.market.MarketStockFeignClient;
import feign.RequestInterceptor;
import feign.MethodMetadata;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FeignClientContractTest {

    private final SpringMvcContract contract = new SpringMvcContract();
    private final RequestInterceptor secretInterceptor =
            new CommonFeignAutoConfiguration().internalApiSecretRequestInterceptor("test-secret");
    private final RequestInterceptor userHeaderInterceptor =
            new CommonFeignAutoConfiguration().userHeaderRequestInterceptor();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void userAccountClientUsesFullInternalPathAndAddsSecretWithoutRequestContext() {
        RequestTemplate template = templateFor(UserAccountFeignClient.class, "getKisToken");

        secretInterceptor.apply(template);
        userHeaderInterceptor.apply(template);

        assertThat(template.method()).isEqualTo("POST");
        assertThat(template.path()).startsWith("/internal/v1/");
        assertThat(template.path()).isEqualTo("/internal/v1/accounts/{userId}/token");
        assertThat(template.headers().get("X-Internal-Secret")).containsExactly("test-secret");
        assertThat(template.headers()).doesNotContainKeys("X-User-Id", "X-User-Role");
    }

    @Test
    void marketStockClientUsesFullInternalPathsAndPropagatesRequestHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        request.addHeader("X-User-Role", "USER");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate quote = templateFor(MarketStockFeignClient.class, "getMarketStockQuote");
        secretInterceptor.apply(quote);
        userHeaderInterceptor.apply(quote);

        assertThat(quote.method()).isEqualTo("GET");
        assertThat(quote.path()).isEqualTo("/internal/v1/stocks/{stockCode}/quote");
        assertThat(quote.headers().get("X-Internal-Secret")).containsExactly("test-secret");
        assertThat(quote.headers().get("X-User-Id")).contains(request.getHeader("X-User-Id"));
        assertThat(quote.headers().get("X-User-Role")).contains("USER");
    }

    private static RequestTemplate templateFor(Class<?> clientType, String methodName) {
        MethodMetadata metadata = new SpringMvcContract().parseAndValidateMetadata(clientType).stream()
                .filter(candidate -> candidate.configKey().contains("#" + methodName + "("))
                .findFirst()
                .orElseThrow();
        return metadata.template();
    }
}
