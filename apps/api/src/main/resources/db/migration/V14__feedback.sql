-- V14: feedback 테이블 + RLS 정책
-- 베타 피드백 수집 채널 (버그/문의/제안).
-- INSERT 는 익명·인증 모두 허용, SELECT/UPDATE/DELETE 는 service_role only.

CREATE TABLE IF NOT EXISTS public.feedback (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID        REFERENCES auth.users(id) ON DELETE SET NULL,
    email       VARCHAR(255),
    type        VARCHAR(32) NOT NULL CHECK (type IN ('bug', 'question', 'suggestion')),
    subject     VARCHAR(200) NOT NULL,
    body        TEXT        NOT NULL,
    url         VARCHAR(500),
    user_agent  VARCHAR(500),
    status      VARCHAR(32) NOT NULL DEFAULT 'open'
                CHECK (status IN ('open', 'in_progress', 'resolved', 'closed')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_feedback_created_at ON public.feedback(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_feedback_status     ON public.feedback(status);
CREATE INDEX IF NOT EXISTS idx_feedback_type       ON public.feedback(type);

ALTER TABLE public.feedback ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "feedback_insert_public" ON public.feedback;
CREATE POLICY "feedback_insert_public" ON public.feedback
    FOR INSERT
    TO anon, authenticated
    WITH CHECK (true);

DROP POLICY IF EXISTS "feedback_read_service" ON public.feedback;
CREATE POLICY "feedback_read_service" ON public.feedback
    FOR SELECT
    TO service_role
    USING (true);

DROP POLICY IF EXISTS "feedback_update_service" ON public.feedback;
CREATE POLICY "feedback_update_service" ON public.feedback
    FOR UPDATE
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS "feedback_delete_service" ON public.feedback;
CREATE POLICY "feedback_delete_service" ON public.feedback
    FOR DELETE
    TO service_role
    USING (true);

COMMENT ON TABLE public.feedback IS '베타 피드백 수집 (버그/문의/제안) — INSERT 공개, 조회/수정은 service_role 전용';
