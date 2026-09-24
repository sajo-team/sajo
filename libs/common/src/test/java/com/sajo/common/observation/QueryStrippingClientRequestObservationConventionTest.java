package com.sajo.common.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.mock.http.client.MockClientHttpRequest;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class QueryStrippingClientRequestObservationConventionTest {

    private static final String RESOLVED_URL =
            "https://openapivts.koreainvestment.com:29443/uapi/domestic-stock/v1/trading/inquire-psbl-order"
                    + "?CANO=50000001&ACNT_PRDT_CD=01&PDNO=005930";

    private final QueryStrippingClientRequestObservationConvention convention =
            new QueryStrippingClientRequestObservationConvention();

    @Test
    void 값이_채워진_문자열을_템플릿으로_넘겨도_uri_태그에는_경로만_남는다() {
        // KisTrClient처럼 UriComponentsBuilder로 완성한 문자열을 .uri(String)에 넘기면 그게 그대로 템플릿이 된다
        ClientRequestObservationContext context = context(
                "/uapi/domestic-stock/v1/trading/inquire-psbl-order?CANO=50000001&ACNT_PRDT_CD=01&PDNO=005930");

        Map<String, String> low = tags(convention.getLowCardinalityKeyValues(context));

        assertThat(low).containsEntry("uri", "/uapi/domestic-stock/v1/trading/inquire-psbl-order");
        assertThat(String.join(",", low.values())).doesNotContain("50000001");
    }

    @Test
    void http_url_태그도_쿼리스트링을_잘라_계좌번호가_span에_남지_않는다() {
        ClientRequestObservationContext context = context("/uapi/domestic-stock/v1/trading/inquire-psbl-order");

        Map<String, String> high = tags(convention.getHighCardinalityKeyValues(context));

        assertThat(high).containsEntry("http.url",
                "https://openapivts.koreainvestment.com:29443/uapi/domestic-stock/v1/trading/inquire-psbl-order");
    }

    @Test
    void 쿼리스트링이_없으면_기본_convention과_같다() {
        ClientRequestObservationContext context = context("/internal/v1/accounts/{userId}");

        assertThat(tags(convention.getLowCardinalityKeyValues(context)))
                .containsEntry("uri", "/internal/v1/accounts/{userId}");
    }

    @Test
    void 템플릿이_없으면_기본값_none을_유지한다() {
        ClientRequestObservationContext context = context(null);

        assertThat(tags(convention.getLowCardinalityKeyValues(context))).containsEntry("uri", "none");
    }

    @Test
    void span_이름에는_호출대상_호스트와_쿼리스트링을_뺀_경로가_들어간다() {
        ClientRequestObservationContext context = context(
                "/uapi/domestic-stock/v1/trading/inquire-psbl-order?CANO=50000001&ACNT_PRDT_CD=01&PDNO=005930");

        assertThat(convention.getContextualName(context))
                .isEqualTo("http get openapivts.koreainvestment.com /uapi/domestic-stock/v1/trading/inquire-psbl-order");
    }

    @Test
    void 템플릿이_없으면_span_이름에서_경로를_생략한다() {
        // market-service KisApiClient처럼 .uri(uriBuilder -> ...)를 쓰면 템플릿이 없어 uri가 none이 된다
        assertThat(convention.getContextualName(context(null)))
                .isEqualTo("http get openapivts.koreainvestment.com");
    }

    private ClientRequestObservationContext context(String uriTemplate) {
        ClientRequestObservationContext context =
                new ClientRequestObservationContext(new MockClientHttpRequest(HttpMethod.GET, URI.create(RESOLVED_URL)));
        context.setUriTemplate(uriTemplate);
        return context;
    }

    private Map<String, String> tags(KeyValues keyValues) {
        return StreamSupport.stream(keyValues.spliterator(), false)
                .collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));
    }
}
