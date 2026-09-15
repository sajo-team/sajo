package com.sajo.user_service.account.repository.command;

import com.sajo.user_service.account.domain.KisTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface KisTokenStatusCommandRepository extends JpaRepository<KisTokenStatus, UUID> {

    // (user_id, token_type) unique 제약을 이용한 원자적 upsert.
    @Modifying
    @Query(value = """
            INSERT INTO p_kis_token_status
                (id, user_id, token_type, event_type, error_code, error_message, last_event_at, created_at, updated_at)
            VALUES
                (:id, :userId, :tokenType, :eventType, :errorCode, :errorMessage, :lastEventAt, now(), now())
            ON CONFLICT (user_id, token_type) DO UPDATE SET
                event_type = EXCLUDED.event_type,
                error_code = EXCLUDED.error_code,
                error_message = EXCLUDED.error_message,
                last_event_at = EXCLUDED.last_event_at,
                updated_at = now()
            WHERE p_kis_token_status.last_event_at <= EXCLUDED.last_event_at
            """, nativeQuery = true)
    void upsert(
            @Param("id") UUID id, @Param("userId") UUID userId, @Param("tokenType") String tokenType,
            @Param("eventType") String eventType, @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage, @Param("lastEventAt") Instant lastEventAt);
}
