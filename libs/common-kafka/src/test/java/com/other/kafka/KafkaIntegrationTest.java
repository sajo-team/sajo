package com.other.kafka;

import com.other.TestApplication;
import com.sajo.other.kafka.KafkaTestMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@EmbeddedKafka(partitions = 1, topics = "test-topic")
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@DisplayName("Kafka 실제 브로커(embedded) 통합 테스트")
class KafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private RecordingListener recordingListener;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Test
    @DisplayName("보낸 메시지를 리스너가 원래 타입 그대로 받는다")
    void producerAndListenerRoundTripPreservesType() throws InterruptedException {
        // 컨슈머가 파티션 할당(rebalance)을 끝내기 전에 보내면 메시지를 놓칠 수 있어서 먼저 대기
        ContainerTestUtils.waitForAssignment(registry.getListenerContainer("test-listener"), 1);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        kafkaTemplate.send("test-topic", new KafkaTestMessage("hello", now));

        KafkaTestMessage received = recordingListener.poll();

        assertThat(received).isEqualTo(new KafkaTestMessage("hello", now));
    }
}
