package com.sajo.operation_service.service.diagnostics.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HostDiagnosticsQueriesTest {

    @Test
    @DisplayName("cpuUsage 쿼리는 idle 모드를 제외한 호스트 CPU 사용률을 계산한다")
    void cpuUsage_excludesIdleMode() {
        String query = HostDiagnosticsQueries.cpuUsage();

        assertThat(query)
                .contains("node_cpu_seconds_total")
                .contains("mode=\"idle\"");
    }

    @Test
    @DisplayName("cpuUsage 쿼리는 rules.yml의 HighNodeCpuUsage와 동일하게 instance, application으로 집계한다")
    void cpuUsage_groupsByInstanceAndApplication() {
        String query = HostDiagnosticsQueries.cpuUsage();

        assertThat(query).contains("avg by (instance, application)");
    }

    @Test
    @DisplayName("memoryUsage 쿼리는 MemAvailable 대비 사용률을 계산한다")
    void memoryUsage_usesMemAvailableRatio() {
        String query = HostDiagnosticsQueries.memoryUsage();

        assertThat(query)
                .contains("node_memory_MemAvailable_bytes")
                .contains("node_memory_MemTotal_bytes");
    }

    @Test
    @DisplayName("diskAvailableRatio 쿼리는 tmpfs/overlay/squashfs를 제외한 여유 비율을 계산한다")
    void diskAvailableRatio_excludesVirtualFilesystems() {
        String query = HostDiagnosticsQueries.diskAvailableRatio();

        assertThat(query)
                .contains("node_filesystem_avail_bytes")
                .contains("node_filesystem_size_bytes")
                .contains("tmpfs|overlay|squashfs");
    }
}
