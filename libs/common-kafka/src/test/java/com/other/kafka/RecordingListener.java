package com.other.kafka;

import com.sajo.other.kafka.KafkaTestMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
class RecordingListener {

    private final BlockingQueue<KafkaTestMessage> received = new LinkedBlockingQueue<>();

    @KafkaListener(id = "test-listener", topics = "test-topic", groupId = "test-group")
    public void onMessage(KafkaTestMessage message) {
        received.add(message);
    }

    KafkaTestMessage poll() throws InterruptedException {
        return received.poll(10, TimeUnit.SECONDS);
    }
}
