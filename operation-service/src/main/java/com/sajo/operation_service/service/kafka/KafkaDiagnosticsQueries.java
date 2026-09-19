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

    // 아래 4개는 KafkaConsumerGroupStrategy 전용 - 알람 자신의 consumergroup/topic 라벨로 파라미터화된다.

    // _sum은 이미 group+topic 단위 합계라, 그룹이 여러 토픽을 구독 중이면 토픽별로 다시 sum해서 그룹 전체 lag를 본다.
    static String lagForGroup(String consumergroup) {
        return "sum(kafka_consumergroup_lag_sum{consumergroup=\"%s\"})".formatted(consumergroup);
    }

    static String membersForGroup(String consumergroup) {
        return "kafka_consumergroup_members{consumergroup=\"%s\"}".formatted(consumergroup);
    }

    static String lagForGroupTopic(String consumergroup, String topic) {
        return "kafka_consumergroup_lag_sum{consumergroup=\"%s\", topic=\"%s\"}".formatted(consumergroup, topic);
    }

    // 파티션 합산 - 특정 토픽 하나의 초당 유입량(컨슈머그룹과 무관)
    static String topicMessageRate(String topic) {
        return "sum(rate(kafka_topic_partition_current_offset{topic=\"%s\"}[5m]))".formatted(topic);
    }
}
