package com.sajo.operation_service.controller.dto.request;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// Alertmanager webhook_configs가 보내는 페이로드 - 지금 단계에 필요한 필드만 매핑함
public record AlertManagerWebhookRequest(
        String status,      // 이 배치 전체의 상태("firing"/"resolved") - 개별 alerts[].status랑 보통 같음
        List<Alert> alerts  // 같은 그룹으로 묶여서 온 알람들
) {
    public record Alert(
            String status,                  // 이 알람 하나의 상태("firing"/"resolved")
            Map<String, String> labels,      // alertname, application 등 - rules.yml의 labels + Prometheus 스크랩 라벨
            Map<String, String> annotations, // summary, description - rules.yml에 적어둔 그 텍스트
            Instant startsAt                 // 이 알람이 firing 시작한 시각 - PrometheusClient 조회 시점으로 그대로 씀
    ) {
    }
}
