package com.sajo.operation_service.service.strategy;

import com.sajo.operation_service.client.PrometheusQueryResult;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.AlertNames;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

// sajo-node 그룹처럼 own snapshot이 호스트 스냅샷과 동일해서 별도 조회가 필요 없는 alertname에 등록한다.
// alertname 라우팅을 alertnameStrategyRegistry 맵 하나로 통일하기 위한 전략 - 아무것도 조회하지 않고
// 빈 결과만 반환한다(코드 리뷰 반영, 별도 예외 목록 없이 맵만으로 라우팅).
@Component
public class NoOpStrategy implements AlertDiagnosisStrategy {

    @Override
    public Set<String> alertnames() {
        return Set.of(
                AlertNames.HIGH_NODE_CPU_USAGE,
                AlertNames.HIGH_NODE_MEMORY_USAGE,
                AlertNames.NODE_DISK_LOW,
                AlertNames.NODE_DISK_WILL_FILL_IN_24H
        );
    }

    @Override
    public StrategyDiagnosis diagnose(AlertManagerWebhookRequest.Alert alert, Instant time) {
        return new StrategyDiagnosis(time, Map.of());
    }
}
