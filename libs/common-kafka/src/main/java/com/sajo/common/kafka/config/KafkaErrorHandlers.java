package com.sajo.common.kafka.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 라이브러리 기본 에러 핸들러(재시도만, DLT 없음)로는 부족하고 DLT까지 필요한 서비스가
 * 자기 {@code @Configuration}에서 갖다 쓰는 헬퍼. 자동으로 빈 등록되지 않음 - DLT 토픽을
 * 누가 모니터링할지는 서비스 책임이라 라이브러리가 강제로 켜주지 않음.
 */
public final class KafkaErrorHandlers {

    private KafkaErrorHandlers() {
    }

    public static DefaultErrorHandler withDlt(KafkaTemplate<Object, Object> template) {
        return withDlt(template, 1000L, 3);
    }

    public static DefaultErrorHandler withDlt(KafkaTemplate<Object, Object> template, long retryIntervalMs, long retryCount) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition())
        );
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, retryCount));
        // poison pill(역직렬화 실패)은 재시도해도 매번 똑같이 실패하니 재시도 없이 바로 DLT로 보냄
        handler.addNotRetryableExceptions(DeserializationException.class);
        return handler;
    }
}
