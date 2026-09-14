-- =========================================================
-- User Service - Account Domain Follow-up Improvements
-- =========================================================

-- =========================================================
-- 1. Account 유니크 제약을 partial unique index로 교체
-- =========================================================

-- 기존엔 unique_column(활성: 고정값 0 / 삭제: 자기 id)으로 소프트 삭제된 row끼리
-- 유니크 제약이 겹치는 걸 우회했음 (Account.softDelete). 이제 partial unique index로
-- 대체 - deleted_at IS NULL인 row(활성 계좌)끼리만 유니크하면 되므로 우회용 컬럼 자체가
-- 필요 없어짐.

ALTER TABLE user_account.p_accounts
    DROP CONSTRAINT IF EXISTS uq_account_user_id;

ALTER TABLE user_account.p_accounts
    DROP CONSTRAINT IF EXISTS uq_account_no_hash;

CREATE UNIQUE INDEX IF NOT EXISTS uq_account_user_id
    ON user_account.p_accounts (user_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_account_no_hash
    ON user_account.p_accounts (account_no_hash)
    WHERE deleted_at IS NULL;

ALTER TABLE user_account.p_accounts
    DROP COLUMN IF EXISTS unique_column;
