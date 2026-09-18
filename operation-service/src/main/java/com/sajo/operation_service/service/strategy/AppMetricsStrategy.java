package com.sajo.operation_service.service.strategy;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.app.DiagnosticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

// user-service/market-service/trading-service 공통 - 알람의 application 라벨로 어느 서비스인지
// 판단해서 기존 DiagnosticsService(1단계에서 작성됨)를 그대로 재사용한다. 서비스마다 별도 클래스가
// 필요 없다: application 값만 다를 뿐 조회 로직 자체는 동일하기 때문.
// ServiceDown처럼 "다운"류 알람은 이 전략이 아니라 별도 전략(직전 시점 조회)을 써야 한다 - 서비스가
// 죽으면 이 전략이 조회하는 4개 지표가 전부 0으로 나와 의미가 없다(1단계에서 실측 확인됨).
@Service
@RequiredArgsConstructor
public class AppMetricsStrategy implements AlertDiagnosisStrategy {

    private final DiagnosticsService diagnosticsService;

    @Override
    public Map<String, PrometheusQueryResult> diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        String application = ApplicationLabels.require(alert);
        return diagnosticsService.collect(application, time);
    }
}
