package com.sajo.operation_service.service.diagnostics.host;

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

    // 연결끊김류(Down) 전략 전용 - "대상이 죽음"과 "네트워크 문제로 연결만 끊김"을 구분할 근거를 준다.
    // 모든 알람에 공통으로 붙는 collect()와 달리, 이건 Down 계열 전략들만 own snapshot에 포함시킨다.
    public Map<String, PrometheusQueryResult> collectNetworkForConnectionDown(Instant time) {
        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>();

        metrics.put("호스트 네트워크 에러율(패킷/초)", prometheusClient.query(HostDiagnosticsQueries.networkErrorRate(), time));
        metrics.put("호스트 네트워크 드롭율(패킷/초)", prometheusClient.query(HostDiagnosticsQueries.networkDropRate(), time));
        metrics.put("TCP 연결 타임아웃율(회/초)", prometheusClient.query(HostDiagnosticsQueries.tcpTimeoutRate(), time));

        return metrics;
    }
}
