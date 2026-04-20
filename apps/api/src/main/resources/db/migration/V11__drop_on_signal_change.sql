-- Phase 4.5.2: 죽은 토글 제거 (notification-ui-cleanup)
-- on_signal_change 컬럼은 실제 발송 로직이 구현되지 않은 UI-only 토글이었음.
-- 향후 AI 시그널 알림이 필요해지면 별도 migration 으로 재도입.

ALTER TABLE notification_settings
    DROP COLUMN IF EXISTS on_signal_change;
