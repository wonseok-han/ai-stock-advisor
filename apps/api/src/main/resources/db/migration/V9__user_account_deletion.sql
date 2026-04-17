-- V9__user_account_deletion.sql
-- Phase 4.5: 회원 탈퇴 soft delete 지원.
-- 탈퇴 요청 시 deleted_at 기록, 로컬 데이터는 보관 (개인정보 보관 정책).

CREATE TABLE deleted_accounts (
    user_id     UUID            NOT NULL PRIMARY KEY,
    email       VARCHAR(255),
    deleted_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    reason      VARCHAR(100)
);

COMMENT ON TABLE  deleted_accounts           IS '탈퇴한 계정 기록. 개인정보 보관 정책(2년)에 따라 보관 후 배치 삭제.';
COMMENT ON COLUMN deleted_accounts.user_id   IS 'Supabase Auth user UUID.';
COMMENT ON COLUMN deleted_accounts.email     IS '탈퇴 시점 이메일 (보관용).';
COMMENT ON COLUMN deleted_accounts.reason    IS '탈퇴 사유 (선택).';
