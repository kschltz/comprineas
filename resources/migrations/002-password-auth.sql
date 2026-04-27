-- Migration 002: Password Authentication
-- Extends users table with password_hash and adds password_reset_tokens table
-- Per PRD-0002 and ADR-0007

-- Add password_hash column to existing users table (nullable: magic-link-only users)
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- Password reset tokens table (structure mirrors magic_link_tokens with 30-min expiry)
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          SERIAL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_email_created
ON password_reset_tokens (email, created_at);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expires
ON password_reset_tokens (expires_at);

-- Login rate limiting: track failed login attempts per IP
CREATE TABLE IF NOT EXISTS login_attempts (
    id          SERIAL PRIMARY KEY,
    ip_address  VARCHAR(45) NOT NULL,
    attempted_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_ip_attempted
ON login_attempts (ip_address, attempted_at);