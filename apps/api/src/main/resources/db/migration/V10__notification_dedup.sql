-- 가격 변동 푸시 알림 중복 억제용 상태 컬럼.
-- 히스테리시스(트리거=임계값, 리셋=임계값*ratio) + 쿨다운 판정에 사용.
--
-- 기존 레코드는 (NULL, false)로 초기화되며,
-- 첫 체크 사이클에서 조건 충족 시 정상적으로 1회 발송 후 상태가 기록된다.
--
-- PostgreSQL 11+ 에서 ADD COLUMN DEFAULT 는 fast default 로 처리되어
-- 테이블 rewrite 없이 즉시 반영됨.

ALTER TABLE notification_settings
    ADD COLUMN last_notified_at TIMESTAMPTZ,
    ADD COLUMN last_triggered_above BOOLEAN NOT NULL DEFAULT false;
