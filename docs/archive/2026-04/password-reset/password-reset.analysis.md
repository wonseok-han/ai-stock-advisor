# password-reset — Gap Analysis Report

## 분석 개요

| 항목 | 값 |
|---|---|
| Feature | password-reset |
| Design | `docs/02-design/features/password-reset.design.md` (v0.1, 2026-04-20) |
| Plan | `docs/01-plan/features/password-reset.plan.md` |
| 구현 Branch/Commit | `feat/password-reset` @ `2326e46` |
| PR | https://github.com/wonseok-han/ai-stock-advisor/pull/22 |
| 분석 대상 파일 | 5개 (신규 4 + 수정 1) |
| 분석 일자 | 2026-04-20 |

## 종합 점수

| 카테고리 | 점수 | 상태 |
|---|:---:|:---:|
| 설계 일치도 (FR/구현) | 98% | 양호 |
| 아키텍처 준수 (Clean Architecture) | 100% | 양호 |
| 컨벤션 준수 (Naming/Import) | 100% | 양호 |
| 보안 요구사항 반영 | 100% | 양호 |
| **전체 Match Rate** | **99%** | **Gate 통과 (≥ 90%)** |

---

## FR (Functional Requirements) 체크리스트

| FR | 요구사항 | 상태 | 구현 위치 |
|---|---|:---:|---|
| FR-01 | 로그인 페이지에 "비밀번호를 잊으셨나요?" 링크 노출 | 구현됨 | `apps/web/src/app/auth/login/page.tsx:27-34` |
| FR-02 | `/auth/forgot-password` 이메일 입력 폼 | 구현됨 | `forgot-password-form.tsx:56-87` |
| FR-03 | `resetPasswordForEmail(email, { redirectTo })` 호출 | 구현됨 | `forgot-password-form.tsx:29-31` |
| FR-04 | 제출 성공 시 안내 (이메일 존재 여부 무관) | 구현됨 | `forgot-password-form.tsx:42-53` |
| FR-05 | 메일 링크 → `/auth/callback` → `/auth/reset-password` | 구현됨 | `callback/route.ts:14,21` (기존 `next` 쿼리 재사용) |
| FR-06 | `/auth/reset-password` 세션 확인 | 구현됨 | `reset-password-form.tsx:23-39` |
| FR-07 | 새 비밀번호 + 확인 입력 (`minLength=6`, 일치 검증) | 구현됨 | `reset-password-form.tsx:45-49, 112, 131` |
| FR-08 | `updateUser({ password })` → 홈 이동 + `refresh()` | 구현됨 | `reset-password-form.tsx:59, 66-67` |
| FR-09 | 에러 영문 메시지 한국어 매핑 | 구현됨 | `forgot-password-form.tsx:90-99`, `reset-password-form.tsx:154-166` |
| FR-10 | 이미 로그인된 사용자 접근 시 리다이렉트 | 의도적 생략 | Plan FR-10 "선택" + Design §11 명시 |

## NFR (Non-Functional Requirements) 체크리스트

| NFR | 요구사항 | 상태 | 근거 |
|---|---|:---:|---|
| NFR-01 | 파일명 kebab-case | 구현됨 | 5개 파일 전부 준수 |
| NFR-02 | BE 변경 0 | 구현됨 | 모두 `apps/web/` 범위 |
| NFR-03 | Next.js 16 App Router 규격 | 구현됨 | metadata export, default export, `'use client'` 분리 정상 |
| NFR-04 | 기존 auth 폼 스타일 일관성 | 구현됨 | login/signup 동일 Tailwind 클래스 세트 |
| NFR-05 | 다크모드 지원 | 구현됨 | `dark:` 변형 전반 적용 |
| NFR-06 | `make web-check` / `make web-build` 통과 | 구현됨 | 로컬 확인 완료 (0 errors, 새 route 2개 Static 등록) |
| NFR-07 | PR 1건 squash merge | 진행중 | PR #22 존재, merge 전 |
| NFR-08 | 투자 자문 면책 문구 불필요 | 구현됨 | auth 범위 외 |

---

## 상태 머신 준수

### forgot-password-form (Design §2.2.1)

| 상태 | 설계 | 구현 | 일치 |
|---|---|---|:---:|
| `idle` | email input + submit | 초기 `useState('idle')` + form | ✅ |
| `loading` | submit 비활성 + "처리 중..." | `disabled` + 버튼 텍스트 분기 | ✅ |
| `sent` | 안내 박스 | line 42-53 녹색 박스 | ✅ |
| `error` | 에러 메시지 | line 75-77 | ✅ |

### reset-password-form (Design §2.2.2)

| 상태 | 설계 | 구현 | 일치 |
|---|---|---|:---:|
| `checking` | 세션 확인 스켈레톤 | line 70-76 | ✅ |
| `invalid` | 만료/무효 + 재요청 링크 | line 78-97 | ✅ |
| `ready` | 입력 폼 | line 99-151 | ✅ |
| `loading` | 비활성 + "변경 중..." | line 145, 148 | ✅ |
| `error` | 에러 + 재시도 | line 139-141 | ✅ |
| 성공 | `router.push('/')` + `refresh()` | line 66-67 | ✅ |

## 파일별 구현 대응

| 설계상 파일 | 실제 파일 | 상태 |
|---|---|:---:|
| `app/auth/forgot-password/page.tsx` | 동일 | ✅ |
| `features/auth/forgot-password-form.tsx` | 동일 | ✅ (+ `mapErrorMessage` 헬퍼) |
| `app/auth/reset-password/page.tsx` | 동일 | ✅ |
| `features/auth/reset-password-form.tsx` | 동일 | ✅ (+ `mapErrorMessage` 헬퍼) |
| `app/auth/login/page.tsx` | 수정 | ✅ |
| `app/auth/callback/route.ts` | 불변 | ✅ |

## 보안 (Design §6) 준수

| 항목 | 상태 | 근거 |
|---|:---:|---|
| 링크 1회성/1시간 만료 | Supabase 자동 | 안내 문구 반영 |
| 이메일 존재 여부 비노출 | ✅ | 분기 없이 `sent` 일괄 표시 |
| 세션 가드 | ✅ | `getUser()` 검증 → invalid 분기 |
| PKCE | Supabase 자동 | callback route 재사용 |
| CSRF | Supabase 자동 | SDK 내부 처리 |
| Rate limiting | Supabase + 메시지 매핑 | `mapErrorMessage` "rate limit" |
| HTTPS 강제 | Vercel 자동 | — |

---

## 차이점 요약

### Missing (Design O, 구현 X)
없음.

### Added (Design X, 구현 O) — 양호한 추가
- `mapErrorMessage` 헬퍼 함수 (양 폼 파일 하단) — Design §4.1 에러 매핑 테이블을 런타임 분기로 실체화. 설계 의도 충실 반영.

### Changed (Design ≠ 구현)
없음. 설계 예시 코드와 실제 구현이 거의 1:1 일치.

---

## 강점

1. **설계 코드 샘플 ↔ 실제 구현 1:1 일치** — Design §3.1~3.4 예시가 그대로 구현됨.
2. **에러 매핑 테이블 실체화** — 표만 제시된 설계를 `mapErrorMessage` 함수로 구체화.
3. **불변 영역 완전 준수** — callback/login-form/signup-form/auth-provider/supabase client 미변경 확인.
4. **FR-10 의도적 생략이 Design §11 에 명시** → 누락이 아닌 결정.

## 개선 권고 (선택적, 비블로커)

1. FE에서 `password.length < 6` 선검증 추가 시 불필요한 Supabase 호출 절감 가능 (NFR 수준).
2. 수동 테스트 시나리오 T1~T12 실제 수행 결과를 Report 단계에서 기록.

---

## 결론

- **Match Rate: 99%** — Gate (≥ 90%) **통과**
- 미구현 FR: 0건 (FR-10은 의도된 생략)
- 설계 위반: 0건
- 반복 개선 불필요
- **다음 단계**: `/pdca report password-reset` → `/pdca archive password-reset`
