package com.sajo.operation_service.client.dto.response;

import java.util.List;
import java.util.Map;

// Prometheus HTTP API(GET /api/v1/query) 응답 - value는 [timestamp, "숫자문자열"] 형태로 옴
public record PrometheusQueryResponse(
        String status,
        Data data
) {
    public record Data(
            String resultType,
            List<Result> result
    ) {
    }

    public record Result(
            Map<String, String> metric,
            List<Object> value
    ) {
    }
}
