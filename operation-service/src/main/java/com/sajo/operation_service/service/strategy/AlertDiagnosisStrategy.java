package com.sajo.operation_service.service.strategy;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;

import java.time.Instant;
import java.util.Set;

// 알람 종류(alertname)마다 동작 자체가 다른 진단 로직을 캡슐화한다.
// HostDiagnosticsService(공통)/DependencyMappingService(정적 매핑)와 달리, 알람별로 "무엇을 어떻게
// 조회할지"가 서로 다르므로 alertname -> 구현체로 갈리는 전략 패턴을 쓴다.
// 어떤 alertname이 어떤 구현체를 쓰는지는 구현체 스스로 alertnames()로 선언한다(하나의 구현체가
// 여러 alertname을 담당할 수 있음, 예: AppMetricsStrategy) - AlertAnalyzer는 Spring이 주입해주는
// List<AlertDiagnosisStrategy>를 순회하며 이 선언들만으로 라우팅 맵을 자동 구성한다. 새 전략을
// 추가해도 AlertAnalyzer를 건드릴 필요가 없다.
public interface AlertDiagnosisStrategy {

    // 해당 전략을 사용하는 알람 이름들
    Set<String> alertnames();

    // metrics는 HostDiagnosticsService/DependencyMappingService/DiagnosticsService와 동일하게
    // "설명 텍스트 -> 조회 결과" 맵으로 통일해서 AlertAnalyzer가 프롬프트에 그대로 이어붙일 수 있게 한다.
    // queryTime은 실제로 이 조회에 쓴 시각 - lookback 없는 전략은 그대로 받은 time을, lookback을
    // 적용하는 전략(Down류)은 그 결과 시각을 돌려줘서 AlertAnalyzer가 프롬프트에 정확히 반영한다.
    StrategyDiagnosis diagnose(AlertManagerWebhookRequest.Alert alert, Instant time);
}
