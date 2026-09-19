package com.sajo.operation_service.service.strategy.kafka;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;
import com.sajo.operation_service.service.strategy.DownAlertLookback;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.AlertNames;
import com.sajo.operation_service.service.diagnostics.kafka.KafkaDiagnosticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

// ConsumerStalled/FallingBehind/NoMembers/GroupMissing/MessageDeadLettered 5개 alertname을
// 전부 이 클래스 하나가 담당한다 - 대상(토픽/컨슈머그룹)이 토픽/그룹 개수만큼 늘어나도 Java 클래스를
// 늘릴 필요 없이, 알람 자신의 consumergroup/topic 라벨을 그대로 읽어 PromQL을 파라미터화한다.
@Component
@RequiredArgsConstructor
public class KafkaConsumerGroupStrategy implements AlertDiagnosisStrategy {

    private final KafkaDiagnosticsService kafkaDiagnosticsService;

    @Override
    public Map<String, PrometheusQueryResult> diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        String consumergroup = KafkaConsumerLabels.consumergroup(alert);
        String topic = KafkaConsumerLabels.topic(alert);
        if (consumergroup == null && topic == null) {
            throw new IllegalArgumentException(
                    "consumergroup/topic 라벨이 둘 다 없는 알람. alertname=" + alert.labels().get("alertname"));
        }

        // ConsumerGroupMissing만 "그룹이 사라짐"류(다른 Down 알람과 동일한 성격)라 lookback이 필요하다.
        // 나머지 4개는 그룹/토픽이 여전히 지표를 내는 "살아있지만 저하된" 상태라 lookback 없이 현재 시점을 본다.
        Instant queryTime = AlertNames.CONSUMER_GROUP_MISSING.equals(alert.labels().get("alertname"))
                ? time.minus(DownAlertLookback.VALUE)
                : time;

        return kafkaDiagnosticsService.collectForConsumerGroup(consumergroup, topic, queryTime);
    }
}
