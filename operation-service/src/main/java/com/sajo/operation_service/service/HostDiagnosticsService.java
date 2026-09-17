package com.sajo.operation_service.service;

import com.sajo.operation_service.client.PrometheusClient;
import com.sajo.operation_service.client.PrometheusQueryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// 이 프로젝트는 단일 호스트에 모든 컨테이너가 함께 떠 있어서, 호스트 자원 부족이 서로 무관해 보이는
// 여러 알람의 공통 원인일 수 있다. 그래서 알람 종류와 무관하게 모든 분석에 공통으로 붙는다.
@Service
@RequiredArgsConstructor
public class HostDiagnosticsService {

    private final PrometheusClient prometheusClient;

    public Map<String, PrometheusQueryResult> collect(Instant time) {
        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>();

        metrics.put("호스트 CPU 사용률(0~1)", prometheusClient.query(HostDiagnosticsQueries.cpuUsage(), time));
        metrics.put("호스트 메모리 사용률(0~1)", prometheusClient.query(HostDiagnosticsQueries.memoryUsage(), time));
        metrics.put("호스트 디스크 여유 비율(0~1)", prometheusClient.query(HostDiagnosticsQueries.diskAvailableRatio(), time));

        return metrics;
    }
}
