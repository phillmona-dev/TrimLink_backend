CREATE TABLE user_device_tokens (
    id             UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token          VARCHAR(512) NOT NULL UNIQUE,
    platform       VARCHAR(20)  NOT NULL CHECK (platform IN ('ANDROID','IOS','WEB')),
    device_id      VARCHAR(150),
    app_version    VARCHAR(50),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_seen_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at     TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255)
);

CREATE INDEX idx_user_device_tokens_user ON user_device_tokens(user_id);
CREATE INDEX idx_user_device_tokens_active ON user_device_tokens(user_id, active);
