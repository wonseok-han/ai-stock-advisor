-- Testcontainers (plain postgres:16-alpine) 에서
-- Supabase 전용 객체를 stub 으로 생성하기 위한 초기화 스크립트.
--
-- 프로덕션 환경은 Supabase 가 아래 객체를 기본 제공하므로 migration 이 그대로 성공하지만,
-- 로컬/CI 의 plain Postgres 에는 없어 Flyway 가 실패함.
-- 이 파일은 Flyway 실행 전에 Testcontainers 가 한 번 돌려 호환성을 확보한다.
--
-- 참고: Supabase 의 실 auth.users 와 스키마는 훨씬 풍부하지만,
-- 테스트에서 필요한 것은 FK 참조와 role 존재 여부뿐이므로 최소 구현만 둔다.

CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.users (
    id UUID PRIMARY KEY
);

DO $$ BEGIN
    CREATE ROLE anon;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE ROLE authenticated;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE ROLE service_role;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
