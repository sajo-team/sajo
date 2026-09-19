package com.sajo.operation_service.service.strategy.app;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;

import java.util.regex.Pattern;

// application 라벨을 PromQL 템플릿에 그대로 꽂아 넣는 Strategy들이 공유하는 검증.
// 서비스명(예: user-service)이라 영문자/숫자/하이픈만 허용 - 아니면 PromQL 인젝션이 될 수 있음.
final class ApplicationLabels {

    private static final Pattern APPLICATION_LABEL_PATTERN = Pattern.compile("^[a-zA-Z0-9-]+$");

    private ApplicationLabels() {
    }

    static String require(AlertManagerWebhookRequest.Alert alert) {
        String application = alert.labels().get("application");
        if (application == null) {
            throw new IllegalArgumentException(
                    "application 라벨이 없는 알람. alertname=" + alert.labels().get("alertname"));
        }
        if (!APPLICATION_LABEL_PATTERN.matcher(application).matches()) {
            throw new IllegalArgumentException(
                    "application 라벨 형식이 올바르지 않은 알람. application=" + application);
        }
        return application;
    }
}
