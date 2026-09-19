package com.sajo.operation_service.service.diagnostics.app;

import com.sajo.operation_service.client.PrometheusClient;
import com.sajo.operation_service.client.PrometheusQueryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DiagnosticsService {

    private final PrometheusClient prometheusClient;

    public Map<String, PrometheusQueryResult> collect(String application, Instant time) {
        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>();

        metrics.put("p99 지연시간(초)", prometheusClient.query(AppDiagnosticsQueries.p99Latency(application), time));
        metrics.put("5xx 에러율(0~1)", prometheusClient.query(AppDiagnosticsQueries.errorRate(application), time));
        metrics.put("CPU 사용률(0~1)", prometheusClient.query(AppDiagnosticsQueries.cpuUsage(application), time));
        metrics.put("Heap(Old Gen) 사용률(0~1)", prometheusClient.query(AppDiagnosticsQueries.heapUsage(application), time));
        metrics.put("HikariCP 커넥션 대기(pending)", prometheusClient.query(AppDiagnosticsQueries.hikariPoolPending(application), time));
        metrics.put("GC pause 시간 비율(0~1)", prometheusClient.query(AppDiagnosticsQueries.gcOverhead(application), time));

        return metrics;
    }
}
