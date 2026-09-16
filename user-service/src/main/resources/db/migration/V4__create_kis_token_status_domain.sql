-- =========================================================
-- User Service - KisTokenStatus (현재 상태 스냅샷) 도입
-- =========================================================

-- 관리자용 사용자별 최신 토큰 발급 상태 목록 조회(KisTokenLogQueryService.getTokenStatuses)가
-- p_kis_token_logs(append-only 이력) 전체를 NOT EXISTS 자기조인으로 훑던 문제를 해결한다.
-- (인덱스로 실측 검증했으나 옵티마이저가 Hash Anti Join을 계속 선택해 효과 없었음 -
-- KisTokenLogQueryRepository의 기존 TODO 참고.)
--
-- 이 테이블은 사용자+토큰타입 조합별 "현재 상태" 1건만 upsert로 유지한다 - row 수가
-- (유저 수 x 토큰타입 수)로 고정돼있어 이력이 아무리 쌓여도 이 조회는 느려지지 않는다.
-- p_kis_token_logs는 계속 순수 append-only 이력으로 남긴다 (p_account_oauth_logs/p_accounts와
-- 동일 패턴).

CREATE TABLE IF NOT EXISTS user_account.p_kis_token_status (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,
    token_type VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    error_code VARCHAR(255),
    error_message VARCHAR(255),
    last_event_at TIMESTAMPTZ NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT uq_kis_token_status_user_token UNIQUE (user_id, token_type)
);

-- 기존에 이미 쌓여있던 이력에서 사용자+토큰타입별 최신 1건씩 백필한다.
-- (이 INSERT ... SELECT DISTINCT ON은 배포 시점에 딱 한 번만 실행되는 일회성 비용이라,
-- p_kis_token_logs를 정렬하는 데 시간이 좀 걸려도 반복 조회 성능 문제와는 무관하다.)
INSERT INTO user_account.p_kis_token_status
    (id, user_id, token_type, event_type, error_code, error_message, last_event_at, created_at)
SELECT DISTINCT ON (user_id, token_type)
    gen_random_uuid(), user_id, token_type, event_type, error_code, error_message, created_at, created_at
FROM user_account.p_kis_token_logs
ORDER BY user_id, token_type, created_at DESC, id DESC
ON CONFLICT (user_id, token_type) DO NOTHING;
