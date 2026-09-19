package com.sajo.operation_service.service.kafka;

// KafkaDiagnosticsService가 쓰는 PromQL 조립만 담당.
final class KafkaDiagnosticsQueries {

    private KafkaDiagnosticsQueries() {
    }

    // kafka-exporter 프로세스는 스크랩돼도(generic up==1) 실제 브로커 연결은 끊길 수 있어서
    // rules.yml의 KafkaBrokerDown과 동일하게 exporter가 인식하는 브로커 수를 쓴다(0이면 끊김).
    static String brokerCount() {
        return "kafka_brokers";
    }

    // 죽기 직전 트래픽 수준 - 전체 토픽/파티션 오프셋 증가량 합산
    static String messageRate() {
        return "sum(rate(kafka_topic_partition_current_offset[5m]))";
    }
}
