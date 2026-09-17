package com.sajo.operation_service.client;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Prometheus 쿼리 하나의 결과 - "매칭 없음"과 "조회 실패"를 0.0으로 뭉개지 않고 값으로 구분한다
public record PrometheusQueryResult(
        String promql,
        List<Series> series,
        String error
) {

    public static PrometheusQueryResult success(String promql, List<Series> series) {
        return new PrometheusQueryResult(promql, series, null);
    }

    public static PrometheusQueryResult failure(String promql, String error) {
        return new PrometheusQueryResult(promql, List.of(), error);
    }

    public boolean successful() {
        return error == null;
    }

    // LLM 프롬프트에 그대로 들어갈 텍스트로 스스로를 표현한다 - 실패/데이터없음/정상을 구분해서 보여줌
    public String toPromptText() {
        if (!successful()) {
            return "query=" + promql + "\nerror=" + error;
        }
        if (series.isEmpty()) {
            return "query=" + promql + "\nresult=데이터 없음";
        }

        String values = series.stream()
                .map(item -> "labels=" + item.labels() + ", value=" + item.value())
                .collect(Collectors.joining("\n"));

        return "query=" + promql + "\n" + values;
    }

    public record Series(Map<String, String> labels, String value) {
    }
}
