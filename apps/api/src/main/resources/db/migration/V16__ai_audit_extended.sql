-- V16: ai_signal_audit 확장 응답 컬럼 추가
-- 참조: docs/02-design/features/ai-analysis-deepening.design.md §3.2
--
-- v2 스키마 필드(beginner_explanation, indicator_interpretation, news_impact, what_to_watch)는
-- 모두 nullable 이므로 기존 컬럼(rationale, risks, summary_ko)과 분리해 단일 JSONB 로 저장한다.
-- raw_response 와 중복되지만, 후속 분석·리플레이 시 스키마가 안정적인 extended_response 를 선호.

ALTER TABLE ai_signal_audit
    ADD COLUMN extended_response JSONB;

COMMENT ON COLUMN ai_signal_audit.extended_response IS
    'v2 응답 확장 필드(beginner_explanation, indicator_interpretation, news_impact, what_to_watch) JSON.'
    ' 누락/이전 버전은 NULL.';

-- 부분 인덱스: v2 응답을 생성한 레코드만 색인 (샘플링/분석 용)
CREATE INDEX idx_ai_signal_audit_extended_presence
    ON ai_signal_audit ((extended_response IS NOT NULL))
    WHERE extended_response IS NOT NULL;
