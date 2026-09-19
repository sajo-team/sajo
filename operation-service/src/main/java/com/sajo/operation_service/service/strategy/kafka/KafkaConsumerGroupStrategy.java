package com.sajo.operation_service.service.strategy.kafka;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.AlertNames;
import com.sajo.operation_service.service.diagnostics.kafka.KafkaDiagnosticsService;
import com.sajo.operation_service.service.strategy.AlertDiagnosisStrategy;
import com.sajo.operation_service.service.strategy.StrategyDiagnosis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

// ConsumerStalled/FallingBehind/NoMembers/GroupMissing/MessageDeadLettered 5개 alertname을
// 전부 이 클래스 하나가 담당한다 - 대상(토픽/컨슈머그룹)이 토픽/그룹 개수만큼 늘어나도 Java 클래스를
// 늘릴 필요 없이, 알람 자신의 consumergroup/topic 라벨을 그대로 읽어 PromQL을 파라미터화한다.
@Component
@RequiredArgsConstructor
public class KafkaConsumerGroupStrategy implements AlertDiagnosisStrategy {

    // absent()는 Prometheus lookback_delta(5분) 유예 후에야 참이 되고 거기에 for(5분)가 더해져서,
    // startsAt이 이미 실제 소멸 시점보다 10분 뒤다 - 2분 lookback으론 여전히 죽은 이후를 보게 됨.
    // 12분으로 안전마진 확보(다른 Down류의 DownAlertLookback 2분과는 별개).
    private static final Duration CONSUMER_GROUP_MISSING_LOOKBACK = Duration.ofMinutes(12);

    private final KafkaDiagnosticsService kafkaDiagnosticsService;

    @Override
    public Set<String> alertnames() {
        return Set.of(
                AlertNames.CONSUMER_STALLED,
                AlertNames.CONSUMER_FALLING_BEHIND,
                AlertNames.CONSUMER_NO_MEMBERS,
                AlertNames.CONSUMER_GROUP_MISSING,
                AlertNames.MESSAGE_DEAD_LETTERED
        );
    }

    @Override
    public StrategyDiagnosis diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        String consumergroup = KafkaConsumerLabels.consumergroup(alert);
        String topic = KafkaConsumerLabels.topic(alert);
        if (consumergroup == null && topic == null) {
            throw new IllegalArgumentException(
                    "consumergroup/topic 라벨이 둘 다 없는 알람. alertname=" + alert.labels().get("alertname"));
        }

        // ConsumerGroupMissing만 lookback 필요, 나머지 4개는 살아있는 상태라 현재 시점을 본다.
        Instant queryTime = AlertNames.CONSUMER_GROUP_MISSING.equals(alert.labels().get("alertname"))
                ? time.minus(CONSUMER_GROUP_MISSING_LOOKBACK)
                : time;

        return new StrategyDiagnosis(queryTime, kafkaDiagnosticsService.collectForConsumerGroup(consumergroup, topic, queryTime));
    }
}
