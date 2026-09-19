package com.sajo.operation_service.service.strategy.kafka;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import org.springframework.lang.Nullable;

import java.util.regex.Pattern;

// consumergroup/topic 라벨을 PromQL 템플릿에 그대로 꽂아 넣는 KafkaConsumerGroupStrategy가 쓰는 검증.
// ApplicationLabels와 달리 alertname마다 둘 중 하나만 있어도 정상이라(예: ConsumerNoMembers는
// consumergroup만) 없으면 null을 반환하고, 있는데 형식이 이상할 때만 예외를 던진다.
final class KafkaConsumerLabels {

    // 토픽/그룹명은 점(.)과 하이픈(-)을 쓰므로 ApplicationLabels보다 허용 문자를 넓힌다.
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private KafkaConsumerLabels() {
    }

    @Nullable
    static String consumergroup(AlertManagerWebhookRequest.Alert alert) {
        return validate(alert, "consumergroup");
    }

    @Nullable
    static String topic(AlertManagerWebhookRequest.Alert alert) {
        return validate(alert, "topic");
    }

    @Nullable
    private static String validate(AlertManagerWebhookRequest.Alert alert, String labelName) {
        String value = alert.labels().get(labelName);
        if (value == null) {
            return null;
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    labelName + " 라벨 형식이 올바르지 않은 알람. " + labelName + "=" + value);
        }
        return value;
    }
}
