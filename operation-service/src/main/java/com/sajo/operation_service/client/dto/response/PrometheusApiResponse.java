package com.sajo.operation_service.client.dto.response;

import java.util.List;
import java.util.Map;

// Prometheus HTTP API(GET /api/v1/query) 응답 - value는 [timestamp, "숫자문자열"] 형태로 옴
// status가 "success"가 아니면 errorType/error에 Prometheus 자신의 실패 사유가 담겨온다
public record PrometheusApiResponse(
        String status,
        Data data,
        String errorType,
        String error
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
