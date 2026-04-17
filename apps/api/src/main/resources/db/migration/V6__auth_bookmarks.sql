-- Phase 4.1: 북마크 테이블
CREATE TABLE bookmarks (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    ticker VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, ticker)
);

CREATE INDEX idx_bookmarks_user_id ON bookmarks(user_id);
