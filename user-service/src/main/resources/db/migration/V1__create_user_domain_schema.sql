-- =========================================================
-- User Service - User(Auth) Domain Initial Flyway Migration
-- =========================================================

CREATE SCHEMA IF NOT EXISTS user_account;


-- =========================================================
-- 1. User
-- =========================================================

CREATE TABLE IF NOT EXISTS user_account.p_users (
    id UUID PRIMARY KEY,

    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL DEFAULT 'USER',

    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ,
    updated_by UUID,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,

    CONSTRAINT uq_users_email UNIQUE (email)
);
