---
template: plan
version: 1.0
feature: feedback
date: 2026-04-20
author: wonseok-han
project: AI Stock Advisor
status: Draft
---

# feedback Plan

## Executive Summary

| 관점 | 요약 |
|---|---|
| **Problem** | 베타 단계 서비스임에도 사용자가 버그를 발견하거나 문의/제안을 전달할 공식 창구가 없다. GitHub Issues 링크만으론 일반 사용자 진입장벽이 높고, 이메일은 스팸·미수신 리스크가 있어 초기 피드백 루프가 단절된 상태. |
| **Solution** | Supabase 기존 인프라만 활용해 `/feedback` 페이지 + `feedback` 테이블을 추가. 유형(버그/문의/제안) 선택 + 제목 + 본문 + 이메일(로그인 시 자동 채움)만으로 제출, RLS 로 INSERT 공개·SELECT 는 서비스 롤 only. 관리자 조회는 **Supabase Dashboard 쿼리**로 대체해 관리자 UI 구현 0. 스팸 방어는 허니팟 + 클라이언트 rate-limit(localStorage 기반) + 본문 길이 제한으로 MVP 수준 커버. |
| **Function UX Effect** | 푸터 "피드백" 링크 → `/feedback` → 유형 선택 + 내용 작성 → 제출 → 감사 안내. 로그인 사용자는 이메일·user_id 자동 주입으로 입력 부담 최소. 비로그인 사용자도 이메일 수기 입력으로 제출 가능. 제출 직후 "저장되었습니다 — 영업일 기준 N일 내 확인" 안내. |
| **Core Value** | 베타 피드백 수집 채널 확보 → 버그 조기 발견 + 사용자 요구 파악 가속. 외부 서비스(Tally/Google Forms) 대신 자체 구현으로 브랜드 일관성 유지, 투자 자문 면책 원칙(본인 책임 문구)과 자연스럽게 결합. BE 변경 0 (Supabase 로 완결), 관리 UI 0 (Dashboard 재사용)으로 1인 개발 리소스 최적. |

## 1. Goal

- **G1 (제출 페이지)**: `/feedback` 에서 유형/제목/본문/이메일 입력 후 Supabase `feedback` 테이블에 insert.
- **G2 (진입점)**: 전역 푸터에 "피드백 보내기" 링크 추가 (인증 여부 무관 접근 가능).
- **G3 (로그인 UX)**: 로그인 시 `user_id` + `email` 자동 주입, 비로그인 시 이메일만 입력.
- **G4 (RLS 보안)**: 익명/인증 모두 INSERT 가능, SELECT/UPDATE/DELETE 는 서비스 롤만.
- **G5 (스팸 방어)**: 허니팟 필드 + 본문 길이 제한(10~2000자) + 클라이언트 쿨다운(60초).
- **G6 (관리)**: 별도 관리자 UI 없이 Supabase Dashboard SQL Editor/Table Editor 로 조회·응답 처리.

## 2. Non-Goals

- **관리자 대시보드 UI**: `/admin/feedback` 같은 페이지. Supabase Dashboard 로 충분.
- **이메일 자동 응답**: 제출 시 사용자에게 확인 메일 발송. SMTP 연동 필요, MVP 범위 아님.
- **상태 라이프사이클 UI**: open/in-progress/resolved/closed 상태 변경 UI. DB 컬럼만 두고 Dashboard 에서 수동 처리.
- **첨부 파일 업로드**: 스크린샷 등. Supabase Storage 연동 + RLS 복잡도 증가 → 베타 후 재검토.
- **hCaptcha / Turnstile**: 베타 트래픽 규모에선 과도. 허니팟 + 쿨다운으로 충분 판단.
- **실시간 알림**: Slack/Discord webhook 연동. Dashboard 주기 확인으로 갈음.
- **GitHub Issues 자동 연동**: 제출 내용을 GitHub Issues 로 자동 생성. 중복 관리 증가.
- **BE(Spring Boot) API 추가**: Supabase 테이블 INSERT 로 완결. Spring 변경 0.
- **다국어**: 한국어 단일. Phase 이후 국제화 시 재검토.

## 3. Requirements

### 3.1 Functional Requirements

| FR | 요구사항 | 수용 기준 |
|----|---------|-----------|
| FR-01 | 전역 푸터에 "피드백 보내기" 링크 노출 | `Footer` 컴포넌트에 `<Link href="/feedback">` 추가, 법적 고지 링크와 동일 영역 |
| FR-02 | `/feedback` 페이지 진입 시 폼 노출 | `FeedbackForm` 컴포넌트 — 유형 select + 제목 + 본문 + 이메일 input |
| FR-03 | 유형 select: `bug` / `question` / `suggestion` 3종 | 한국어 라벨: "버그 신고" / "문의" / "제안". 기본값 `bug` |
| FR-04 | 로그인 사용자는 이메일 필드 자동 채움 + readOnly | `auth-provider` 에서 세션 구독 → email prefill. `user_id` 는 hidden state |
| FR-05 | 비로그인 사용자는 이메일 수기 입력 (required) | `type=email` + required, 간단 형식 검증 |
| FR-06 | 제목 1~100자, 본문 10~2000자 검증 | 클라이언트 제출 전 `minLength`/`maxLength` + 에러 메시지 |
| FR-07 | 제출 시 Supabase `feedback` 테이블 INSERT | client SDK `supabase.from('feedback').insert(...)` 사용, 페이지 URL/User-Agent 포함 |
| FR-08 | 제출 성공 시 감사 안내 화면 전환 | "피드백을 받았습니다" + 푸터 링크로 홈 복귀 유도 |
| FR-09 | 에러 발생 시 한국어 메시지 표시 | Supabase 에러 메시지 매핑 (예: RLS 위반 → 재시도 안내) |
| FR-10 | 허니팟 필드 (CSS hidden) 채워지면 제출 무효 처리 | 봇 차단 — 입력되면 조용히 성공 반환 (스팸 시그널 수집) |
| FR-11 | 60초 쿨다운 (localStorage `feedback:lastSubmittedAt`) | 중복 제출 방지. 남은 시간 안내 |
| FR-12 | `user_agent`, `url` 자동 수집 | `window.navigator.userAgent`, `window.location.href` 에서 추출해 insert payload 에 포함 |
| FR-13 | 투자 자문 면책 문구 노출 | "본 서비스는 투자 자문이 아닙니다. 투자 판단과 책임은 사용자 본인에게 있습니다." — 폼 하단 짧게 |

### 3.2 Non-Functional Requirements

| NFR | 요구사항 |
|-----|---------|
| NFR-01 | 파일명 kebab-case (`feedback-form.tsx`, `feedback/page.tsx`) |
| NFR-02 | 컴포넌트 PascalCase (`FeedbackForm`), 함수 camelCase (`submitFeedback`) |
| NFR-03 | BE 변경 0 (Spring Boot `apps/api/` 미변경) |
| NFR-04 | Next.js 16 App Router 규격 (`page.tsx`, `metadata` export) |
| NFR-05 | 다크모드 지원 (`dark:` 클래스) — 기존 auth 폼과 스타일 일관성 |
| NFR-06 | `make web-check` / `make web-build` 통과 (0 errors) |
| NFR-07 | PR 1건 squash merge (feat/feedback 브랜치) |
| NFR-08 | Flyway 미사용 (Supabase 네이티브 테이블) — 대신 마이그레이션 SQL 을 `supabase/migrations/` 또는 design 문서에 기록 |
| NFR-09 | RLS 정책 문서화 — Supabase Dashboard SQL 수동 적용 절차 포함 |

### 3.3 Security / Compliance

- **RLS (Row Level Security)**: `INSERT` 는 anon + authenticated 모두 허용, `SELECT/UPDATE/DELETE` 는 `service_role` only.
- **PII 최소화**: 이메일 외 개인정보 저장 금지 (이름·전화 수집 안 함).
- **이메일 검증**: 브라우저 `type=email` 검증만 (인증 메일 발송 X — MVP 범위 외).
- **스팸 방어 계층**: 허니팟(봇) → 쿨다운(중복) → 길이 제한(스팸 본문) → RLS(DB 보호).
- **면책 원칙 (CLAUDE.md §4)**: 폼 하단 짧은 고지 포함. 향후 응답 시에도 "투자 자문 아님" 원칙 유지.

## 4. Scope

### In Scope (파일)

| 파일 | 구분 | 역할 |
|---|---|---|
| `apps/web/src/app/feedback/page.tsx` | 신규 | 피드백 페이지 (metadata + layout) |
| `apps/web/src/features/feedback/feedback-form.tsx` | 신규 | 폼 client 컴포넌트 (입력·검증·제출·상태머신) |
| `apps/web/src/features/feedback/types.ts` | 신규 | `FeedbackType`, `FeedbackInsert` 타입 |
| `apps/web/src/components/footer.tsx` (또는 layout) | 수정 | "피드백 보내기" 링크 추가 |
| `docs/02-design/features/feedback.design.md` | 신규 | Design 문서 (다음 단계) |
| `supabase/migrations/YYYYMMDDHHMMSS_create_feedback_table.sql` 또는 design 문서 내 SQL 블록 | 신규 | `feedback` 테이블 + RLS 정책 (수동 적용) |

### Out of Scope

- `apps/api/` 전체 (Spring Boot)
- Admin 페이지 / 대시보드 컴포넌트
- Storage 업로드
- 이메일 발송 파이프라인

## 5. Dependencies / Prerequisites

- **Supabase 프로젝트**: 이미 설정됨 (`@/lib/supabase/client`).
- **기존 `auth-provider`**: 로그인 세션 구독 재사용 — 수정 없음.
- **Footer 컴포넌트 존재 여부**: 확인 필요. 없다면 `app/layout.tsx` 에 직접 링크 추가.
- **Supabase Dashboard 권한**: `feedback` 테이블 생성 + RLS 정책 적용 수동 실행 필요.

## 6. Risks

| # | 리스크 | 영향 | 대응 |
|---|---|---|---|
| R1 | RLS 정책 오설정으로 INSERT 실패 | 높음 | Design 문서에 SQL 블록 명시 + 로컬 개발 DB 에 선적용 후 검증 |
| R2 | 허니팟·쿨다운 우회 봇 유입 | 중간 | 실사용 로그 주기 관찰 후 필요 시 hCaptcha 추가 (후속 feature) |
| R3 | 로그인 사용자의 이메일 readOnly 전환으로 타계정 이메일 제출 불가 | 낮음 | "로그인 계정으로 제출됩니다" 안내 + 로그아웃 상태로 재제출 가능 |
| R4 | 본문에 투자 자문 요청이 올 경우 응답 원칙 흔들림 | 중간 | 폼 안내문에 "개별 투자 자문은 제공하지 않습니다" 명시 |
| R5 | Supabase 테이블 스키마 변경 시 FE 타입 drift | 낮음 | `types.ts` 단일 소스, 향후 `supabase gen types` 연계 가능 |
| R6 | Footer 컴포넌트 미존재 시 레이아웃 수정 범위 확장 | 낮음 | Design 단계에서 레이아웃 진단 후 범위 확정 |

## 7. Success Criteria

1. **기능 완결**: 로그인/비로그인 양쪽에서 `/feedback` 제출 성공, `feedback` 테이블에 row 생성 확인.
2. **진입점**: 푸터 링크에서 1-click 도달.
3. **보안**: RLS 적용 상태에서 anon 클라이언트가 SELECT 실패 확인 (권한 없음).
4. **UX**: 제출 성공 화면 → 홈 복귀, 에러 시 한국어 안내, 쿨다운·허니팟 작동.
5. **품질 지표**: `make web-check` 0 errors, Gap Analysis Match Rate ≥ 90%.
6. **관리 플로우**: Supabase Dashboard → Table Editor 에서 `feedback` row 조회 가능 확인.

## 8. Next Steps

1. `/pdca design feedback` — Design 문서 작성 (스키마 SQL·RLS·상태머신·파일별 구현).
2. 구현 → `make web-check`/`web-build` 통과 → `feat/feedback` 브랜치 PR.
3. `/pdca analyze feedback` → Match Rate ≥ 90% → `/pdca report feedback` → `/pdca archive feedback`.
4. Supabase Dashboard 에서 테이블 + RLS SQL 수동 실행 (배포 전).
5. Footer 컴포넌트 존재 확인 (없으면 design 단계에서 추가 범위 포함).
