-- Phase 4.5.3: notification-news feature — 뉴스 알림 watermark
ALTER TABLE notification_settings
    ADD COLUMN IF NOT EXISTS last_news_published_at TIMESTAMPTZ;

COMMENT ON COLUMN notification_settings.last_news_published_at IS
  '마지막으로 알림 발송한 뉴스의 published_at. NULL 이면 baseline 필요 (첫 사이클).';
