package com.sajo.operation_service.service.diagnostics.kafka;

import com.sajo.operation_service.client.PrometheusClient;
import com.sajo.operation_service.client.PrometheusQueryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// Kafka 브로커 자체(알람 컨텍스트 없이)의 연결 상태만 다룬다. 컨슈머그룹/토픽 단위 부하(lag 등)는
// KafkaConsumerGroupStrategy가 알람 자신의 consumergroup/topic 라벨을 읽어서 collectForConsumerGroup()으로 조회한다.
@Service
@RequiredArgsConstructor
public class KafkaDiagnosticsService {

    private final PrometheusClient prometheusClient;

    public Map<String, PrometheusQueryResult> collect(Instant time) {
        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>();

        metrics.put("Kafka 브로커 수", prometheusClient.query(KafkaDiagnosticsQueries.brokerCount(), time));

        return metrics;
    }

    // KafkaBrokerDown 전용 - collect()에 없는 트래픽을 더 본다.
    public Map<String, PrometheusQueryResult> collectForConnectionDown(Instant time) {
        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>(collect(time));

        metrics.put("초당 메시지 처리량", prometheusClient.query(KafkaDiagnosticsQueries.messageRate(), time));

        return metrics;
    }

    // 컨슈머그룹/DLT류 알람 전용
    public Map<String, PrometheusQueryResult> collectForConsumerGroup(
            String consumergroup, String topic, Instant time
    ) {
        Map<String, PrometheusQueryResult> metrics = new LinkedHashMap<>();

        if (consumergroup != null) {
            metrics.put("컨슈머그룹 전체 lag", prometheusClient.query(KafkaDiagnosticsQueries.lagForGroup(consumergroup), time));
            metrics.put("컨슈머그룹 멤버 수", prometheusClient.query(KafkaDiagnosticsQueries.membersForGroup(consumergroup), time));
            if (topic != null) {
                metrics.put("해당 토픽 lag", prometheusClient.query(KafkaDiagnosticsQueries.lagForGroupTopic(consumergroup, topic), time));
            }
        }
        if (topic != null) {
            metrics.put("해당 토픽 초당 유입량", prometheusClient.query(KafkaDiagnosticsQueries.topicMessageRate(topic), time));
        }

        return metrics;
    }
}
