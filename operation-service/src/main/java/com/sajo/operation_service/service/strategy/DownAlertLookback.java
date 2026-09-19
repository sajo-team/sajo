package com.sajo.operation_service.service.strategy;

import java.time.Duration;

// 연결끊김/다운류 알람이 공통으로 쓰는 lookback - 대상이 죽으면 startsAt 시점엔 이미 값이 없어서
// (Redis/Postgres/Mongo/Kafka/서비스 전부 실측 확인됨) 살아있던 마지막 시점을 대신 조회한다.
// 관련 alertname들의 for(30s~1m) + 스크랩/평가 지연(최대 15s)을 감안해도 2분이면
// 죽기 이전 시점을 안전하게 잡는다.
// redis/postgres/mongo/kafka/app 서브패키지의 Strategy들이 같이 쓰므로 public.
public final class DownAlertLookback {

    public static final Duration VALUE = Duration.ofMinutes(2);

    private DownAlertLookback() {
    }
}
