package com.sajo.operation_service.service.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppDiagnosticsQueriesTest {

    @Test
    @DisplayName("p99Latency 쿼리에 application 필터와 actuator 제외 조건이 들어간다")
    void p99Latency_containsApplicationFilterAndExcludesActuator() {
        String query = AppDiagnosticsQueries.p99Latency("trading-service");

        assertThat(query)
                .contains("application=\"trading-service\"")
                .contains("uri!~\"/actuator.*\"")
                .contains("histogram_quantile(0.99");
    }

    @Test
    @DisplayName("errorRate 쿼리는 5xx 비율을 application 기준으로 계산한다")
    void errorRate_containsStatus5xxFilter() {
        String query = AppDiagnosticsQueries.errorRate("trading-service");

        assertThat(query)
                .contains("application=\"trading-service\"")
                .contains("status=~\"5..\"");
    }

    @Test
    @DisplayName("cpuUsage 쿼리는 process_cpu_usage를 application으로 필터링한다")
    void cpuUsage_filtersProcessCpuUsageByApplication() {
        String query = AppDiagnosticsQueries.cpuUsage("trading-service");

        assertThat(query).isEqualTo("process_cpu_usage{application=\"trading-service\"}");
    }

    @Test
    @DisplayName("heapUsage 쿼리는 GC live data 대비 max data 비율을 계산한다")
    void heapUsage_usesGcLiveDataRatio() {
        String query = AppDiagnosticsQueries.heapUsage("trading-service");

        assertThat(query)
                .contains("jvm_gc_live_data_size_bytes{application=\"trading-service\"}")
                .contains("jvm_gc_max_data_size_bytes{application=\"trading-service\"}");
    }
}
