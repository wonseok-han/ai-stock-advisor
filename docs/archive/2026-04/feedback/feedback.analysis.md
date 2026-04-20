# feedback — Gap Analysis Report

## 분석 개요

| 항목 | 값 |
|---|---|
| Feature | feedback |
| Design | `docs/02-design/features/feedback.design.md` (v1.0, 2026-04-20) |
| Plan | `docs/01-plan/features/feedback.plan.md` |
| 구현 Branch/Commit | `feat/feedback` @ `bd1a6e6` |
| PR | https://github.com/wonseok-han/ai-stock-advisor/pull/23 |
| 분석 대상 파일 | 5개 (신규 4 + 수정 1) |
| 분석 일자 | 2026-04-20 |

## 종합 점수

| 카테고리 | 점수 | 가중 |
|---|:---:|:---:|
| Design Match (§3~§5) | 98% | 50% |
| Architecture (§8) | 100% | 20% |
| Convention (§9) | 100% | 20% |
| 파일 존재성 (§1.1) | 100% | 10% |
| **전체 Match Rate** | **99%** | Gate ≥ 90% **통과** |

---

## 파일별 일치도

| Design §1.1 파일 | 실제 구현 | 상태 |
|---|---|:---:|
| `apps/api/src/main/resources/db/migration/V14__feedback.sql` | 52줄 | ✅ |
| `apps/web/src/app/feedback/page.tsx` | 27줄 | ✅ |
| `apps/web/src/features/feedback/feedback-form.tsx` | 260줄 | ✅ |
| `apps/web/src/features/feedback/types.ts` | 36줄 | ✅ |
| `apps/web/src/components/legal/disclaimer-footer.tsx` | +4줄 | ✅ |

## 섹션별 매칭

| 섹션 | 항목 | 매칭 | 비율 |
|---|---|:---:|:---:|
| §3 DB Schema | 15 | 15 | 100% |
| §4.1 types.ts | 5 | 5 | 100% |
| §4.2 feedback-form.tsx | 22 | 22 | 99% (편차 1개: 개선형) |
| §4.3 page.tsx | 4 | 4 | 100% |
| §4.4 disclaimer-footer.tsx | 1 | 1 | 100% |
| §5 에러 매핑 | 5 | 5 | 100% |
| §6 보안 방어층 | 7 | 7 | 100% |
| §8 레이어 배치 | 6 | 6 | 100% |
| §9 컨벤션 | 7 | 7 | 100% |
| **합계** | **72** | **71** | **99%** |

---

## 확인된 편차 (개선형 1건)

### ⚠️ §4.2 이메일 prefill: `useEffect + setState` → 파생 상태

| 항목 | 내용 |
|---|---|
| 위치 | `apps/web/src/features/feedback/feedback-form.tsx:30, 36, 214` |
| Design 샘플 | `useState('')` + `useEffect` 에서 `setEmail(user.email)` 동기화 |
| 실제 구현 | `const [emailInput, setEmailInput] = useState('')` + `const email = user?.email ?? emailInput` (파생) |
| 사유 | React 19 / Next.js 16 린트 규칙 `react-hooks/set-state-in-effect` 가 useEffect 내 setState 를 금지. 파생 상태가 공식 권장 패턴. |
| 평가 | **개선(improvement)** — Design 의도("로그인 시 user.email 자동 주입, 비로그인 수기 입력") 동일 유지, 불필요한 리렌더 감소 |

### ✅ 추가 구현 (Design 초과, 긍정적)

- `aria-label="법적 고지" → "법적 고지 및 피드백"` 업데이트 (disclaimer-footer.tsx:17) — 접근성 개선

## 누락 / 회귀

**없음.** Design 의 모든 요구사항이 구현에 반영됨.

---

## 보안 (§6) 준수

| 방어층 | 상태 | 근거 |
|---|:---:|---|
| 허니팟 (`name="company"`, sr-only) | ✅ | feedback-form.tsx:130-138 |
| 60초 쿨다운 (localStorage) | ✅ | L38-49, L103 |
| 길이 제한 (FE + maxLength) | ✅ | L66-75, L170/188-189 |
| RLS INSERT 공개 / SELECT·UPDATE·DELETE service_role only | ✅ | V14 L26-49 |
| CHECK 제약 (type/status) | ✅ | V14 L9, L14-15 |
| PII 최소화 | ✅ | 이메일 외 수집 없음 |
| `ON DELETE SET NULL` (히스토리 보존) | ✅ | V14 L7 |

## 아키텍처 (§8) 준수

의존성 방향 `page.tsx → feedback-form.tsx → (auth-provider, supabase/client, types)` — Presentation → Infrastructure/Domain 준수, 위반 0건.

---

## 결론

| 항목 | 결과 |
|---|---|
| **Match Rate** | **99%** |
| **Gate (≥ 90%)** | **통과** |
| **미구현 / 회귀** | 0건 |
| **개선형 편차** | 1건 (React 19 린트 대응, 설계 의도 보존) |
| **권장 다음 단계** | `/pdca report feedback` → `/pdca archive feedback` |

반복 개선(`/pdca iterate`) 불필요. 수동 QA 권장 시나리오: T1(비로그인 제출), T2(로그인 prefill readOnly), T5(60초 쿨다운), T6(허니팟), T8(Dashboard SELECT 확인).
