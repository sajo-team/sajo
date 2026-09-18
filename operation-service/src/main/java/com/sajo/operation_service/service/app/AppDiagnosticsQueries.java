package com.sajo.operation_service.service.app;

// AppDiagnosticsSnapshot을 구성하는 PromQL 쿼리 조립만 담당 -
final class AppDiagnosticsQueries {

    private AppDiagnosticsQueries() {
    }

    static String p99Latency(String application) {
        return """
                histogram_quantile(0.99,
                  sum by (le) (
                    rate(http_server_requests_seconds_bucket{application="%s", uri!~"/actuator.*"}[5m])
                  )
                )
                """.formatted(application);
    }

    // rules.yml의 HighErrorRate 알람과 동일한 필터(actuator 제외)를 씀
    static String errorRate(String application) {
        return """
                sum(rate(http_server_requests_seconds_count{application="%s", status=~"5..", uri!~"/actuator.*"}[5m]))
                /
                sum(rate(http_server_requests_seconds_count{application="%s", uri!~"/actuator.*"}[5m]))
                """.formatted(application, application);
    }

    static String cpuUsage(String application) {
        return "process_cpu_usage{application=\"%s\"}".formatted(application);
    }

    // rules.yml의 HighMemoryUsage 알람과 동일한 지표(GC 후 Old Gen 사용률)
    static String heapUsage(String application) {
        return """
                jvm_gc_live_data_size_bytes{application="%s"}
                /
                (jvm_gc_max_data_size_bytes{application="%s"} > 0)
                """.formatted(application, application);
    }
}
