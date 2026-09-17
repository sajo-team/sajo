package com.sajo.operation_service.service.dto;

// sajo-services 그룹(Spring Boot 애플리케이션) 알람에 한정된 최소 진단 세트
public record AppDiagnosticsSnapshot(
        String application,
        double p99LatencySeconds,
        double errorRate,
        double cpuUsage,
        double heapUsageRatio
) {
}
