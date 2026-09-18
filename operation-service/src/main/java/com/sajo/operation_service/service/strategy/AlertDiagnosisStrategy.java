package com.sajo.operation_service.service.strategy;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;

import java.time.Instant;
import java.util.Map;

// 알람 종류(alertname)마다 동작 자체가 다른 진단 로직을 캡슐화한다.
// HostDiagnosticsService(공통)/DependencyMappingService(정적 매핑)와 달리, 알람별로 "무엇을 어떻게
// 조회할지"가 서로 다르므로 alertname -> 구현체로 갈리는 전략 패턴을 쓴다.
// 어떤 alertname이 어떤 구현체를 쓰는지(하나의 구현체가 여러 alertname을 담당할 수 있음, 예:
// AppMetricsStrategy)는 구현체 스스로 선언하지 않고 AlertAnalyzer의 매핑 테이블에서 관리한다.
public interface AlertDiagnosisStrategy {

    // 반환 형식은 HostDiagnosticsService/DependencyMappingService/DiagnosticsService와 동일하게
    // "설명 텍스트 -> 조회 결과" 맵으로 통일해서 AlertAnalyzer가 프롬프트에 그대로 이어붙일 수 있게 한다.
    Map<String, PrometheusQueryResult> diagnose(AlertManagerWebhookRequest.Alert alert, Instant time);
}
