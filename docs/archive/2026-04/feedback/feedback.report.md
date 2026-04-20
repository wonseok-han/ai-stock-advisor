---
template: report
version: 1.0
feature: feedback
date: 2026-04-20
author: wonseok-han
project: AI Stock Advisor
status: Completed
match_rate: 99%
---

# feedback 완료 보고서

## Executive Summary

### 프로젝트 개요

| 항목 | 내용 |
|---|---|
| **기능** | 베타 사용자 피드백 수집 채널 (`/feedback` 페이지) |
| **시작일** | 2026-04-20 |
| **완료일** | 2026-04-20 |
| **소요 시간** | ~1일 |
| **담당자** | wonseok-han |

### 결과 요약

| 지표 | 결과 |
|---|---|
| **설계 일치도 (Match Rate)** | 99% ✅ |
| **구현 파일** | 5개 (신규 4 + 수정 1) |
| **코드 변경** | +1198줄 (+7 files) |
| **BE Java 변경** | 0 (Flyway V14 SQL only) |
| **회귀 이슈** | 0건 |
| **권장 액션** | 확인 완료 → 아카이브 준비 완료 |

### 1.3 핵심 가치 (Value Delivered)

| 관점 | 설명 |
|---|---|
| **Problem** | 베타 단계에서 사용자 버그 신고·문의·제안 공식 채널 부재. 외부 링크 진입장벽 높음. |
| **Solution** | `/feedback` 페이지 (Supabase 직접 INSERT) + RLS 정책 (INSERT 공개, 조회 서비스 롤 전용) + 3중 스팸 방어 (허니팟·60초 쿨다운·길이 제한) 구현. |
| **Function/UX Effect** | 푸터 링크 1-클릭 진입 → 유형/제목/본문/이메일 입력 → 감사 안내. 로그인 사용자 이메일 자동 주입(readonly). 비로그인도 수기 제출 가능. |
| **Core Value** | (1) 베타 버그·사용자 요구 공식 수집 채널 확보. (2) 자체 구현으로 브랜드·면책 일관성 유지. (3) 관리자 UI 0 (Supabase Dashboard 재사용), BE 코드 0 — 1인 개발 리소스 최적화. |

---

## PDCA 사이클 요약

### Plan (계획)

**문서**: `docs/01-plan/features/feedback.plan.md` (v1.0, 2026-04-20)

**주요 내용**:
- 목표 6개 (G1~G6): 페이지 제출 → Supabase INSERT, 로그인 UX, RLS 보안, 스팸 방어, 관리
- 비목표 명확화: 관리자 대시보드 UI 제외, hCaptcha 미채택 (베타 규모 대비 과도), 첨부 파일 미지원
- 요구사항 62개 (FR 13 + NFR 9 + Security/Compliance)
- 성공 기준 6개 (기능 완결, 진입점, 보안, UX, 품질, 관리 플로우)

**결과**: 설계·구현 모두 이 계획을 충실히 따름. 편차 0.

### Design (설계)

**문서**: `docs/02-design/features/feedback.design.md` (v1.0, 2026-04-20)

**주요 내용**:
- 아키텍처: 사용자 → 푸터 링크 → `/feedback` → `FeedbackForm` → Supabase `public.feedback` 직접 INSERT
- 상태 머신: idle → loading → sent/error, 쿨다운·허니팟 조건 포함
- DB 스키마: Flyway V14 (15개 컬럼 + RLS 정책 4개)
- 파일 5개 명시 (types.ts, feedback-form.tsx, page.tsx, disclaimer-footer.tsx 수정, V14__feedback.sql)
- 에러 매핑 5가지 (RLS, CHECK, FK, 네트워크)
- 보안 7개 레이어 (허니팟, 쿨다운, 길이, RLS, CHECK, PII 최소화, ON DELETE SET NULL)
- 구현 순서 10단계 (S1~S10) 제시

**설계 결정** 8개 (D1~D8):
1. Supabase 테이블 직접 INSERT (FE-only, Spring 엔드포인트 신설 안 함)
2. Flyway V14 (기존 스키마 일관성)
3. 관리자 UI 미구현 (Dashboard 대체)
4. 허니팟 + 쿨다운 (hCaptcha 미채택)
5. 익명 제출 허용
6. `user_id ON DELETE SET NULL` (히스토리 보존)
7. 푸터 단일 진입점
8. 허니팟 트리거 시 조용히 성공 처리

**결과**: 구현이 설계를 99% 준수.

### Do (구현)

**Branch**: `feat/feedback` @ `bd1a6e6`  
**PR**: #23 (develop 타겟, squash 대기)

**구현된 파일**:

| 파일 | 유형 | 줄수 | 설명 |
|---|---|:---:|---|
| `V14__feedback.sql` | 신규 | 52 | feedback 테이블 + RLS 정책 (Design §3 정확 준수) |
| `feedback-form.tsx` | 신규 | 260 | 상태머신·검증·Supabase INSERT (useState + useEffect → 파생 상태로 개선) |
| `types.ts` | 신규 | 36 | `FeedbackType`, `FeedbackInsert`, 상수 |
| `app/feedback/page.tsx` | 신규 | 27 | metadata + layout (Design §4.3 정확) |
| `disclaimer-footer.tsx` | 수정 | +4 | "피드백 보내기" 링크 추가 + aria-label 개선 |

**구현 특징**:
- 허니팟 (`name="company"` sr-only), 60초 쿨다운 (localStorage), 길이 제한 (1~100자 제목, 10~2000자 본문) 3중 방어 완성
- 로그인 시 user_id/email 자동 주입 (파생 상태), 비로그인 수기 이메일 입력 모두 지원
- 에러 메시지 한국어화 (RLS, CHECK, FK, 네트워크)
- 다크모드 완전 지원 (`dark:` 클래스)
- 투자 자문 면책 문구 폼 하단에 일관성 있게 표시

**빌드 및 린트**:
- `make web-check`: 0 errors 통과
- `make web-build`: 성공, `/feedback` 라우트 정적 등록 확인
- 컨벤션: 파일명 kebab-case, 컴포넌트·타입 PascalCase, 함수 camelCase 준수

**코드 변경 통계**:
- 신규 파일: 4개
- 수정 파일: 1개 (disclaimer-footer.tsx)
- 총 +1198줄 (docs 포함 +7 files)
- BE Java 변경: 0

### Check (검증)

**문서**: `docs/03-analysis/feedback.analysis.md` (2026-04-20)

**Match Rate**: **99%** ✅ (Gate ≥ 90% 통과)

**종합 점수**:
| 카테고리 | 점수 | 가중 |
|---|:---:|:---:|
| Design Match (§3~§5) | 98% | 50% |
| Architecture (§8) | 100% | 20% |
| Convention (§9) | 100% | 20% |
| 파일 존재성 (§1.1) | 100% | 10% |

**파일별 일치도**:
| 파일 | Design 요청 | 실제 구현 | 상태 |
|---|---|---|:---:|
| V14__feedback.sql | ✅ | 52줄 | ✅ |
| page.tsx | ✅ | 27줄 | ✅ |
| feedback-form.tsx | ✅ | 260줄 | ✅ (편차 1개 개선형) |
| types.ts | ✅ | 36줄 | ✅ |
| disclaimer-footer.tsx | ✅ | +4줄 | ✅ |

**확인된 편차** (개선형 1건):

| 항목 | Design 예시 | 실제 구현 | 평가 |
|---|---|---|---|
| 이메일 prefill | `useState('')` + `useEffect`에서 `setEmail(user.email)` | `const email = user?.email ?? emailInput` (파생 상태) | **개선** — React 19 린트 규칙 `react-hooks/set-state-in-effect` 대응, 불필요한 리렌더 감소, 의도 동일 |

**누락 / 회귀**: 없음.

**보안 준수** (§6 모두 ✅):
- 허니팟, 쿨다운, 길이 제한, RLS, CHECK 제약, PII 최소화, ON DELETE SET NULL

---

## 핵심 메트릭

| 메트릭 | 값 | 평가 |
|---|---|---|
| **Match Rate** | 99% | 우수 (Gate 통과) |
| **파일 완성도** | 5/5 | 100% |
| **설계 결정 준수** | 8/8 | 100% (D1~D8 모두 반영) |
| **편차** | 1건 | 개선형 (React 19 린트) |
| **회귀 이슈** | 0건 | 양호 |
| **빌드 성공** | ✅ | `make web-check/build` 통과 |
| **타입 안정성** | ✅ | TypeScript strict mode 통과 |
| **다크모드 지원** | ✅ | dark: 클래스 완전 적용 |

---

## 주요 설계 결정 (Design §11 요약)

### D1: Supabase 테이블 직접 INSERT

- **선택**: FE 클라이언트에서 RLS INSERT 직접
- **대안**: Spring Boot 엔드포인트 추가
- **근거**: BE 변경 0, FE-only 완결로 1인 개발 리소스 최소화. RLS로 보안 충분.

### D2: Flyway V14 마이그레이션

- **선택**: 기존 `users`/`bookmarks` 컨벤션과 동일하게 Flyway V14 SQL
- **대안**: `supabase/migrations/` 신규 디렉터리 또는 Dashboard 수동 생성
- **근거**: 스키마 관리 일관성 우선. 런타임 자동 적용.

### D3: 관리자 UI 미구현

- **선택**: Supabase Dashboard (service_role) 재사용
- **대안**: `/admin/feedback` 페이지 + 상태 변경 UI
- **근거**: 베타 트래픽 규모 대비 ROI 낮음. Dashboard는 이미 사용 중.

### D4: 허니팟 + 쿨다운 (hCaptcha 미채택)

- **선택**: 클라이언트 허니팟 + localStorage 60초 쿨다운 + 길이 제한
- **대안**: hCaptcha / Cloudflare Turnstile
- **근거**: 베타 단계 트래픽 규모에선 과도. 실사용 로그 모니터링 후 필요 시 후속.

### D5: 익명 제출 허용

- **근거**: 비로그인 사용자의 신고 접근성 보장 (회원가입 중 버그 경험 사용자).

### D6: `user_id ON DELETE SET NULL`

- **근거**: 계정 탈퇴 시 피드백 히스토리 보존 (운영상 추적 가능).

### D7: 푸터 단일 진입점

- **근거**: MVP 범위 최소화. 필요 시 헤더/마이페이지 추가 가능.

### D8: 허니팟 트리거 시 조용히 성공 처리

- **근거**: defense-in-depth — 봇에게 탐지 사실 미노출.

---

## Lessons Learned

### ✅ 잘된 점

1. **파생 상태 패턴 (React 19)**: `useState + useEffect`에서 직접 파생 상태로 전환 — 불필요한 리렌더 제거, 동기화 버그 없음. React 19 린트 규칙과 정확 일치.

2. **Flyway 일관성**: 기존 V1~V13 컨벤션을 따름으로써 스키마 버전 관리 중앙화. 다중 마이그레이션 도구 분산 회피.

3. **RLS 설계**: INSERT 공개, SELECT/UPDATE/DELETE 서비스 롤 전용 정책은 간결하면서도 보안 충분. 클라이언트 권한 분리 명확.

4. **3중 스팸 방어**: 허니팟(봇) → 쿨다운(연속) → 길이(스팸 본문) 계층화. MVP 규모에 적절.

5. **BE 변경 0**: Spring Boot 코드 터치 없이 Flyway SQL만으로 완결 — 팀 협업 없이 1인 완수 가능.

### 🔄 개선 여지

1. **이메일 검증**: 현재 브라우저 `type=email` 검증만. 실수 이메일로 응답 불가 시나리오 가능성.
   - 향후: 확인 메일 발송 (SMTP 연동) 또는 후속 피드백 재제출 시 메일 재입력.

2. **스팸 모니터링**: 현재 로직 기반 방어만. 실사용 로그 집계 필요.
   - 향후: Supabase 대시보드 월별 리포트 작성, hCaptcha 추가 결정.

3. **관리자 워크플로우**: Dashboard 쿼리 수동 방식. 대량 피드백 처리 시 UI 자동화 고려.
   - 향후: `/admin/feedback` 페이지 + 상태 변경/응답 UI (트래픽 ↑시).

### 📌 다음 사이클에 적용할 사항

1. **파생 상태 먼저**: React 19 린트 규칙(`react-hooks/set-state-in-effect`)을 초기 설계에서 고려. useEffect 쓸 일 줄이기.

2. **Flyway 버전 관리 선행**: 신규 인프라 기능(테이블, 정책)은 Flyway 통합부터. 이중화 방지.

3. **RLS 계층화 명시**: 설계 단계에서 권한별 정책(anon, authenticated, service_role) 명확히 구분.

4. **베타 메트릭 사전 정의**: 스팸 필터 필요 여부 판정 기준 (예: "주 100개 이상" → hCaptcha 추가) 미리 협의.

---

## 다음 단계

1. **PR #23 머지 승인**: squash merge to develop
2. **Supabase 실환경 V14 적용**: 로컬/스테이징 동기화 후 실환경 마이그레이션 실행
3. **수동 QA 확인** (선택):
   - T1: 비로그인 제출 + email 수기 입력
   - T2: 로그인 상태 email readOnly
   - T5: 60초 쿨다운 동작
   - T6: 허니팟 우회 시도 (개발자 도구로 채우기) → 조용히 성공 확인
   - T8: Supabase Dashboard `SELECT * FROM public.feedback` → row 조회 가능 확인
4. **아카이브 준비**: `/pdca archive feedback` → `docs/archive/2026-04/feedback/` 이동
5. **상태 업데이트**: .bkit-memory.json 및 PDCA 상태 "completed" 기록

---

## 종합 평가

| 항목 | 결과 | 근거 |
|---|:---:|---|
| **기능 완결** | ✅ | 5/5 파일 구현, 설계 99% 준수 |
| **품질** | ✅ | `make web-check/build` 통과, 타입 안정성, 다크모드 |
| **보안** | ✅ | RLS, CHECK 제약, 3중 스팸 방어, PII 최소화 |
| **일정** | ✅ | ~1일 완수 (1인 개발 최적화) |
| **아키텍처** | ✅ | 의존성 정상, 레이어 분리, 컨벤션 준수 |
| **문서** | ✅ | Plan 과 Design 충실, 편차 명확히 기록 |

**최종 판정**: **완료 승인 (Ready for Archive)**

현재 Match Rate 99%로 Gap 분석 Gate ≥ 90% 만족. 반복 개선(`/pdca iterate`) 불필요.
다음 단계: PR 머지 → Supabase 실환경 적용 → 아카이브.
