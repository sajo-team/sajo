package com.sajo.common.feign;

import feign.Contract;
import feign.MethodMetadata;
import feign.Param;
import feign.RequestLine;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.Target;
import feign.micrometer.FeignContext;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class StandardFeignObservationConventionTest {

    private final StandardFeignObservationConvention convention = new StandardFeignObservationConvention();

    interface StrategyApi {
        @RequestLine("GET /internal/v1/strategies/{strategyId}")
        void getStrategy(@Param("strategyId") String strategyId);
    }

    interface KisInquiryApi {
        @RequestLine("GET /uapi/domestic-stock/v1/trading/inquire-daily-ccld?CANO={CANO}&PDNO={PDNO}")
        void inquire(@Param("CANO") String cano, @Param("PDNO") String pdno);
    }

    @Test
    void 응답이_있으면_Spring_표준_태그키와_경로템플릿으로_기록한다() {
        FeignContext context = new FeignContext(request("http://market-service/internal/v1/strategies/0000-1111"));
        context.setResponse(response(context.getCarrier(), 404));

        Map<String, String> tags = tags(convention.getLowCardinalityKeyValues(context));

        assertThat(tags).containsOnlyKeys("client.name", "method", "uri", "status", "outcome", "exception");
        assertThat(tags)
                .containsEntry("client.name", "market-service")
                .containsEntry("method", "GET")
                .containsEntry("uri", "/internal/v1/strategies/{strategyId}")
                .containsEntry("status", "404")
                .containsEntry("outcome", "CLIENT_ERROR")
                .containsEntry("exception", "none");
    }

    @Test
    void 응답없이_예외로_끝나면_status는_CLIENT_ERROR_outcome은_UNKNOWN() {
        FeignContext context = new FeignContext(request("http://market-service/internal/v1/strategies/0000-1111"));
        context.setError(new SocketTimeoutException("Read timed out"));

        Map<String, String> tags = tags(convention.getLowCardinalityKeyValues(context));

        assertThat(tags)
                .containsEntry("status", "CLIENT_ERROR")
                .containsEntry("outcome", "UNKNOWN")
                .containsEntry("exception", "SocketTimeoutException");
    }

    @Test
    void 성공_응답은_SUCCESS() {
        FeignContext context = new FeignContext(request("http://market-service/internal/v1/strategies/0000-1111"));
        context.setResponse(response(context.getCarrier(), 200));

        assertThat(tags(convention.getLowCardinalityKeyValues(context)))
                .containsEntry("outcome", "SUCCESS");
    }

    @Test
    void uri에서_쿼리스트링은_잘라내고_경로만_남긴다() {
        MethodMetadata metadata = new Contract.Default().parseAndValidateMetadata(KisInquiryApi.class).get(0);
        RequestTemplate template = new RequestTemplate();
        template.methodMetadata(metadata);
        template.feignTarget(new Target.HardCodedTarget<>(KisInquiryApi.class, "kis-order-client", "https://kis"));
        Request request = Request.create(Request.HttpMethod.GET,
                "https://kis/uapi/domestic-stock/v1/trading/inquire-daily-ccld?CANO=50000001&PDNO=005930",
                Map.of(), null, StandardCharsets.UTF_8, template);

        Map<String, String> tags = tags(convention.getLowCardinalityKeyValues(new FeignContext(request)));

        assertThat(tags).containsEntry("uri", "/uapi/domestic-stock/v1/trading/inquire-daily-ccld");
    }

    @Test
    void 메트릭_이름은_Spring과_같고_span_이름에는_호출대상과_경로가_들어간다() {
        FeignContext context = new FeignContext(request("http://market-service/internal/v1/strategies/0000-1111"));

        assertThat(convention.getName()).isEqualTo("http.client.requests");
        assertThat(convention.getContextualName(context))
                .isEqualTo("http get market-service /internal/v1/strategies/{strategyId}");
    }

    @Test
    void Feign_컨텍스트에만_적용된다() {
        assertThat(convention.supportsContext(new FeignContext(request("http://x/a")))).isTrue();
        assertThat(convention.supportsContext(new Observation.Context())).isFalse();
    }

    // MethodMetadata 생성자가 package-private이라 실제 Feign Contract로 인터페이스를 파싱해서 얻는다
    private Request request(String resolvedUrl) {
        MethodMetadata metadata = new Contract.Default().parseAndValidateMetadata(StrategyApi.class).get(0);

        RequestTemplate template = new RequestTemplate();
        template.methodMetadata(metadata);
        template.feignTarget(new Target.HardCodedTarget<>(StrategyApi.class, "market-service", "http://market-service"));

        return Request.create(Request.HttpMethod.GET, resolvedUrl, Map.of(), null, StandardCharsets.UTF_8, template);
    }

    private Response response(Request request, int status) {
        return Response.builder()
                .status(status)
                .request(request)
                .headers(Map.of())
                .build();
    }

    private Map<String, String> tags(KeyValues keyValues) {
        return StreamSupport.stream(keyValues.spliterator(), false)
                .collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));
    }
}
