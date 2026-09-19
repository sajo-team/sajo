package com.sajo.operation_service.service.diagnostics.host;

// 호스트(node-exporter) 스냅샷을 구성하는 PromQL 쿼리 조립만 담당 - 이 프로젝트는 호스트가 하나뿐이라
// application 파라미터가 필요 없음. rules.yml의 sajo-node 그룹 알람과 동일한 공식을 재사용한다.
final class HostDiagnosticsQueries {

    private HostDiagnosticsQueries() {
    }

    // rules.yml의 HighNodeCpuUsage와 동일한 공식
    static String cpuUsage() {
        return """
                1 - avg by (instance, application) (rate(node_cpu_seconds_total{mode="idle"}[5m]))
                """;
    }

    // rules.yml의 HighNodeMemoryUsage와 동일한 공식
    static String memoryUsage() {
        return """
                1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)
                """;
    }

    // rules.yml의 NodeDiskLow와 동일한 공식 - "사용률"이 아니라 "여유 비율"이라는 점에 주의
    // (임계값을 밑돌면 위험한 방향이라 알람 원본과 방향을 맞춰서 헷갈리지 않게 함)
    static String diskAvailableRatio() {
        return """
                node_filesystem_avail_bytes{fstype!~"tmpfs|overlay|squashfs"}
                /
                node_filesystem_size_bytes{fstype!~"tmpfs|overlay|squashfs"}
                """;
    }

    // 아래 3개는 연결끊김류(Down) 전략 전용 - "대상이 죽었다"와 "네트워크가 끊겼다"는 동일한
    // 증상(연결 실패)을 만들어서 구분이 안 되는데, 이 지표들로 후자일 가능성을 확인/기각할 근거를 준다.

    static String networkErrorRate() {
        return """
                sum(rate(node_network_receive_errs_total{device!="lo"}[5m]))
                +
                sum(rate(node_network_transmit_errs_total{device!="lo"}[5m]))
                """;
    }

    static String networkDropRate() {
        return """
                sum(rate(node_network_receive_drop_total{device!="lo"}[5m]))
                +
                sum(rate(node_network_transmit_drop_total{device!="lo"}[5m]))
                """;
    }

    // TCP 연결 자체가 타임아웃난 횟수 - "연결 끊김" 증상과 가장 직접적으로 연결되는 지표.
    static String tcpTimeoutRate() {
        return "rate(node_netstat_TcpExt_TCPTimeouts[5m])";
    }
}
