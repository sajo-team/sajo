package com.sajo.operation_service.service;

import com.sajo.operation_service.client.PrometheusClient;
import com.sajo.operation_service.service.dto.AppDiagnosticsSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DiagnosticsService {
    private final PrometheusClient prometheusClient;

    public AppDiagnosticsSnapshot collect(String application, Instant instant) {
        double p99LatencySeconds = prometheusClient.queryScalar(AppDiagnosticsQueries.p99Latency(application), instant);
        double errorRate = prometheusClient.queryScalar(AppDiagnosticsQueries.errorRate(application), instant);
        double cpuUsage = prometheusClient.queryScalar(AppDiagnosticsQueries.cpuUsage(application), instant);
        double heapUsageRatio = prometheusClient.queryScalar(AppDiagnosticsQueries.heapUsage(application), instant);

        return new AppDiagnosticsSnapshot(application, p99LatencySeconds, errorRate, cpuUsage, heapUsageRatio);
    }
}
