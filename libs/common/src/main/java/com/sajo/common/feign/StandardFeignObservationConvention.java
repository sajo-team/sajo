package com.sajo.common.feign;

import com.sajo.common.observation.ClientSpanNames;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.micrometer.FeignContext;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.Observation;

/*
     feign-micrometer 기본 convention(DefaultFeignObservationConvention)은 http.client.requests 메트릭에
     clientName/http.method/http.url/http.status_code 태그를 붙이는데, 같은 이름의 메트릭을 Spring
     RestClient(Eureka 클라이언트 등)는 client.name/method/uri/status/outcome/exception 태그로 만든다.
     Prometheus는 같은 이름의 메트릭이 같은 태그 키 집합을 가져야 해서, 먼저 등록된 쪽만 남고 나머지는
     등록이 거부된다(Feign 호출 메트릭 유실). Feign 쪽 태그를 Spring 표준 키에 맞춰 하나의 메트릭으로 합친다.

     Spring Cloud OpenFeign은 MicrometerObservationCapability를 커스텀 convention 없이 생성하므로,
     ObservationRegistry에 등록된 GlobalObservationConvention이 기본 convention 대신 적용된다.
*/

public class StandardFeignObservationConvention implements GlobalObservationConvention<FeignContext> {

    private static final String NONE = "none";
    private static final String UNKNOWN = "UNKNOWN";
    // 응답 자체가 없는 경우(연결 실패, 타임아웃 등) - Spring DefaultClientRequestObservationConvention과 동일한 값
    private static final String NO_RESPONSE_STATUS = "CLIENT_ERROR";

    @Override
    public String getName() {
        return "http.client.requests";
    }

    @Override
    public String getContextualName(FeignContext context) {
        Request request = context.getCarrier();
        return ClientSpanNames.of(method(request), clientName(request), uriTemplate(request));
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(FeignContext context) {
        Request request = context.getCarrier();
        Response response = context.getResponse();

        return KeyValues.of(
                "client.name", clientName(request),
                "method", method(request),
                "uri", uriTemplate(request),
                "status", status(response),
                "outcome", outcome(response),
                "exception", exception(context)
        );
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof FeignContext;
    }

    // @FeignClient의 name(user-service, market-service 등) - 인스턴스 host 대신 논리 이름을 써서 카디널리티를 고정한다
    private String clientName(Request request) {
        RequestTemplate template = request == null ? null : request.requestTemplate();
        if (template == null || template.feignTarget() == null) {
            return NONE;
        }
        return template.feignTarget().name();
    }

    private String method(Request request) {
        if (request == null || request.httpMethod() == null) {
            return UNKNOWN;
        }
        return request.httpMethod().name();
    }

    // 실제 값이 치환되기 전의 경로 템플릿(/internal/v1/strategies/{strategyId}) - 실제 URL을 쓰면 ID마다
    // 시계열이 생겨 카디널리티가 폭발하고, KIS 쿼리스트링 같은 민감값이 메트릭 라벨에 남는다
    private String uriTemplate(Request request) {
        RequestTemplate template = request == null ? null : request.requestTemplate();
        if (template == null || template.methodMetadata() == null
                || template.methodMetadata().template() == null) {
            return NONE;
        }
        String url = template.methodMetadata().template().url();
        if (url == null || url.isBlank()) {
            return NONE;
        }
        // 쿼리스트링은 {CANO} 같은 자리표시자뿐이라 API 구분에 쓸모가 없고 라벨만 길어지므로 경로만 남긴다
        int queryStart = url.indexOf('?');
        return queryStart >= 0 ? url.substring(0, queryStart) : url;
    }

    private String status(Response response) {
        return response == null ? NO_RESPONSE_STATUS : String.valueOf(response.status());
    }

    private String outcome(Response response) {
        if (response == null) {
            return UNKNOWN;
        }
        return switch (response.status() / 100) {
            case 1 -> "INFORMATIONAL";
            case 2 -> "SUCCESS";
            case 3 -> "REDIRECTION";
            case 4 -> "CLIENT_ERROR";
            case 5 -> "SERVER_ERROR";
            default -> UNKNOWN;
        };
    }

    private String exception(FeignContext context) {
        Throwable error = context.getError();
        return error == null ? NONE : error.getClass().getSimpleName();
    }
}
