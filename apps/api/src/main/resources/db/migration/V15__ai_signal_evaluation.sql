-- V15__ai_signal_evaluation.sql
-- Phase 5 / signal-accuracy: AI 시그널 N일 후 방향 정합도 평가 결과 (파생 데이터).
-- 참조: docs/02-design/features/signal-accuracy.design.md §3.3

CREATE TABLE ai_signal_evaluation (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id              UUID        NOT NULL
                          REFERENCES ai_signal_audit(id) ON DELETE CASCADE,
    window_days           SMALLINT    NOT NULL CHECK (window_days IN (7, 30)),

    -- 시점 · 가격
    signal_generated_at   TIMESTAMPTZ NOT NULL,
    evaluation_target_at  TIMESTAMPTZ NOT NULL,
    price_at_signal       NUMERIC(12,4) NOT NULL,
    price_at_window_end   NUMERIC(12,4) NOT NULL,
    end_trade_date        DATE        NOT NULL,

    -- 판정
    signal                VARCHAR(16) NOT NULL,
    predicted_direction   VARCHAR(8)  NOT NULL,
    actual_direction      VARCHAR(8)  NOT NULL,
    change_pct            NUMERIC(8,4) NOT NULL,
    hit                   BOOLEAN     NOT NULL,

    evaluated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (audit_id, window_days)
);

CREATE INDEX idx_evaluation_window_evaluated
    ON ai_signal_evaluation (window_days, evaluated_at DESC);

CREATE INDEX idx_evaluation_window_signal
    ON ai_signal_evaluation (window_days, signal);

COMMENT ON TABLE  ai_signal_evaluation                       IS 'AI 시그널 N일 후 방향 정합도 평가 결과 (파생 데이터).';
COMMENT ON COLUMN ai_signal_evaluation.audit_id              IS '평가 원천 ai_signal_audit.id. audit 삭제 시 같이 제거.';
COMMENT ON COLUMN ai_signal_evaluation.window_days           IS '평가 윈도우 (7 또는 30).';
COMMENT ON COLUMN ai_signal_evaluation.signal_generated_at   IS '시그널 생성 시점 (audit 시점과 동일, 집계 최적화용 중복 저장).';
COMMENT ON COLUMN ai_signal_evaluation.evaluation_target_at  IS 'signal_generated_at + window_days. 실제 end_trade_date 는 다음 거래일 shift 가능.';
COMMENT ON COLUMN ai_signal_evaluation.price_at_signal       IS '시그널 생성 시점 가격 (audit.context_payload.quote.price 또는 캔들 fallback).';
COMMENT ON COLUMN ai_signal_evaluation.price_at_window_end   IS 'window 경과 후 종가 (candles.close).';
COMMENT ON COLUMN ai_signal_evaluation.end_trade_date        IS 'price_at_window_end 로 사용한 실제 거래일 (주말/휴일 처리로 target 과 다를 수 있음).';
COMMENT ON COLUMN ai_signal_evaluation.predicted_direction   IS 'UP / FLAT / DOWN. Signal 을 3-class 로 매핑한 값.';
COMMENT ON COLUMN ai_signal_evaluation.actual_direction      IS 'UP / FLAT / DOWN. change_pct 와 flat_threshold 로 결정.';
COMMENT ON COLUMN ai_signal_evaluation.change_pct            IS '(end - signal) / signal * 100 (%).';
COMMENT ON COLUMN ai_signal_evaluation.hit                   IS 'predicted_direction == actual_direction 여부.';
