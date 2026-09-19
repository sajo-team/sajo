package com.sajo.operation_service.service.strategy;

import com.sajo.operation_service.client.PrometheusQueryResult;

import java.time.Instant;
import java.util.Map;

// 전략이 실제로 조회에 쓴 시각(queryTime)과 그 결과(metrics)를 같이 반환한다.
public record StrategyDiagnosis(Instant queryTime, Map<String, PrometheusQueryResult> metrics) {
}
