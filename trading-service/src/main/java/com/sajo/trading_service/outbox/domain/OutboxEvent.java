package com.sajo.trading_service.outbox.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "p_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    private UUID id; //eventId로 넣을 부분

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private Integer eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_body", nullable = false, columnDefinition = "jsonb")
    private JsonNode eventBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public static OutboxEvent create(
            UUID eventId,
            String eventType,
            int eventVersion,
            JsonNode eventBody
    ) {
        OutboxEvent event = new OutboxEvent();

        event.id = eventId;
        event.eventType = eventType;
        event.eventVersion = eventVersion;
        event.eventBody = eventBody;
        event.status = OutboxStatus.PENDING;
        event.retryCount = 0;
        event.createdAt = Instant.now();

        return event;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.claimedAt = null;
    }

    public void increaseRetryCount() {
        this.retryCount++;
    }

    public void markFailed(){
        this.status = OutboxStatus.FAILED;
        this.claimedAt = null;
    }

    public void markPending(){
        this.status = OutboxStatus.PENDING;
        this.claimedAt = null;
    }
}