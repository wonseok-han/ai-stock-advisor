-- V13__drop_unused_tables.sql
-- 미사용 테이블 정리.
--
-- 배경:
--   V1 에서 MVP 스키마를 선제 정의했으나 아래 2 테이블은 실제 코드 경로에서 사용되지 않음.
--   legal_disclaimer_audit 는 향후 면책 문구 버전 감사용으로 남겨 둠.
--
--   1) popular_tickers
--      - 인기 종목 마스터용으로 V1 에 생성 + V2 에서 30 종목 seed.
--      - 실제 로직은 Java 상수 PopularTickerPool.TICKERS 로 하드코딩되어 있음.
--      - 어떤 Repository/JdbcTemplate 도 이 테이블을 SELECT 하지 않음.
--
--   2) ai_signal_history
--      - V1 에서 AI 시그널 이력용으로 생성.
--      - V4 에서 ai_signal_audit 로 대체됨 (AiSignalAuditEntity/Repository 가 실사용).
--      - 구 테이블은 엔티티·리포지토리 없음. 아무도 INSERT/SELECT 하지 않음.
--
-- 안전성:
--   두 테이블 모두 외래키로 참조되지 않음 (V1 ~ V12 전체 스캔 확인).
--   V2 seed 는 popular_tickers 에만 INSERT 했으므로 DROP 과 함께 자연 소멸.
--   Flyway 는 과거 V1/V2/V4 체크섬을 보존 — repair 불필요.

DROP TABLE IF EXISTS popular_tickers;
DROP TABLE IF EXISTS ai_signal_history;
