package com.sajo.operation_service.service.kafka;

import com.sajo.operation_service.client.PrometheusClient;
import com.sajo.operation_service.client.PrometheusQueryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// Kafka 브로커 자체(알람 컨텍스트 없이)의 연결 상태만 다룬다. 컨슈머그룹/토픽 단위 부하(lag 등)는
// DEPENDENCY_MAP이 대상 이름만 알고 어떤 topic/consumergroup인지는 몰라서 여기서 못 다룬다 -
// 그건 나중에 알람 자신의 라벨을 직접 읽는 KafkaConsumerGroupStrategy에서 처리한다.
@Service
@RequiredArgsConstructor
public class KafkaDiagnosticsService {

    private final PrometheusClient prometheusClient;

    public Map<String, PrometheusQueryResult> collect(Instant time) {
        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>();

        metrics.put("Kafka 브로커 수", prometheusClient.query(KafkaDiagnosticsQueries.brokerCount(), time));

        return metrics;
    }
}
