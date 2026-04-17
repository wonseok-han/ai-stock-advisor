-- Phase 4.2: Web Push 알림

CREATE TABLE push_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    endpoint TEXT NOT NULL,
    p256dh TEXT NOT NULL,
    auth TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, endpoint)
);

CREATE TABLE notification_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    ticker VARCHAR(16) NOT NULL,
    price_change_threshold NUMERIC,
    on_new_news BOOLEAN NOT NULL DEFAULT false,
    on_signal_change BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (user_id, ticker)
);

CREATE INDEX idx_notification_settings_user_ticker
    ON notification_settings(user_id, ticker);
