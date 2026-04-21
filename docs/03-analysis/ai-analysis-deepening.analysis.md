# ai-analysis-deepening — Gap Analysis Report

> PDCA Check 단계 산출물. Design(`docs/02-design/features/ai-analysis-deepening.design.md` v0.1, 2026-04-21) vs 구현 (branch `feat/ai-analysis-deepening`, 3 commits).

| 항목 | 값 |
|---|---|
| Feature | ai-analysis-deepening |
| Design Doc | `docs/02-design/features/ai-analysis-deepening.design.md` (v0.1, 2026-04-21) |
| Plan Doc | `docs/01-plan/features/ai-analysis-deepening.plan.md` (v0.1, 2026-04-21) |
| Impl Scope | `apps/api/src/main/java/com/aistockadvisor/ai/{domain,service,infra}/**`, `V16__ai_audit_extended.sql`, `prompts/ai-signal.system.txt`, `apps/web/src/{types/ai-signal.ts, features/stock-detail/ai-signal/**}` |
| Analysis Date | 2026-04-21 |
| **Match Rate** | **93%** |
| 권고 | `/pdca report ai-analysis-deepening` 진입 |

---

## 1. Overall Scores

| Category | Weight | Score | Status |
|---|:---:|:---:|:---:|
| 데이터 모델 (§3.1·§3.2) | 20% | 100% | ✅ |
| ContextAssembler 뉴스 freshness (§3.3) | 10% | 100% | ✅ |
| API / Prompt (§4) | 15% | 100% | ✅ |
| FE UX / graceful degrade (§5) | 20% | 92% | ✅ |
| Error Handling (§6) | 10% | 100% | ✅ |
| Security / Guard (§7) | 5% | 100% | ✅ |
| Test Plan (§8) | 10% | 55% | ⚠️ |
| Forbidden Terms Guard (§10) | 5% | 100% | ✅ |
| Implementation Order (§11.2) | 5% | 91% (10/11, Step 10 Deferred) | ⚠️ |
| **Overall Match Rate** | 100% | **93%** | ✅ |

> 계산 근거: 각 카테고리 가중치 × 점수 합. §8 단위/통합 테스트 신규 추가 없음(설계는 요구)으로 55% 감점, Step 10 dry-run은 운영 검증 단계로 Deferred 처리. 나머지는 design 과 1:1 정합.

---

## 2. 섹션별 검증

### §3.1 Data Model (Domain) — ✅ 100%

| 설계 항목 | 구현 위치 | 검증 |
|---|---|---|
| `AiSignal` record + 기존 6필드 유지 + v2 4필드 nullable | `ai/domain/AiSignal.java:15-34` | ✅ `beginnerExplanation`/`indicatorInterpretation`/`newsImpact`/`whatToWatch` nullable 필드 추가, 기존 필드 순서 보존 |
| `IndicatorInterpretation` record `(indicator, value, meaningKo)` | `ai/domain/IndicatorInterpretation.java` | ✅ 1:1 |
| `NewsImpact` record `(titleKo, impact, reasonKo, hoursAgo)` | `ai/domain/NewsImpact.java` | ✅ 1:1, `hoursAgo`는 `Integer` (nullable) |
| `ImpactDirection` enum POSITIVE/NEGATIVE/NEUTRAL | `ai/domain/ImpactDirection.java` | ✅ 1:1 |
| FE `AiSignal` 타입 확장 (모두 optional) | `apps/web/src/types/ai-signal.ts` | ✅ `beginnerExplanation?`, `indicatorInterpretation?`, `newsImpact?`, `whatToWatch?` + `ImpactDirection` / `IndicatorInterpretation` / `NewsImpact` interface |

### §3.2 V16 Migration — ✅ 100%

| 설계 항목 | 구현 | 검증 |
|---|---|---|
| `ALTER TABLE ai_signal_audit ADD COLUMN extended_response JSONB` | `V16__ai_audit_extended.sql:8-9` | ✅ |
| `COMMENT ON COLUMN` 설명 | V16:11-13 | ✅ |
| 부분 인덱스 `idx_ai_signal_audit_extended_presence` | V16:16-18 | ✅ + **`WHERE extended_response IS NOT NULL`** 추가 (🟡 Positive Added) |
| AiSignalAuditEntity JSONB 매핑 | `AiSignalAuditEntity.java:80-82` | ✅ `extendedResponse: Map<String,Object>` + nullable |

### §3.3 ContextAssembler — News Freshness — ✅ 100%

| 설계 항목 | 구현 | 검증 |
|---|---|---|
| 뉴스 건수 3 → 5 | `ContextAssembler.NEWS_LIMIT = 5` | ✅ |
| `hours_ago` 계산 (`Duration.between`) | `newsOf()` | ✅ |
| freshness tier FRESH(<24h) / RECENT(<72h) / STALE | `freshnessOf()` | ✅ 경계값 정확 |
| `hoursAgo < 0` 방어 (미래 발행 시각) | ContextAssembler | 🟡 Positive Added |

### §4 API / Prompt — ✅ 100%

| 설계 항목 | 구현 | 검증 |
|---|---|---|
| JSON 응답 4 신규 필드 (camelCase) | `AiSignalService` validator 매핑 | ✅ |
| 시스템 프롬프트 v2 스키마 명시 | `prompts/ai-signal.system.txt` | ✅ 예시 문장 포함 |
| freshness 가이드 (Rule 7) | system.txt | ✅ |
| forbidden 안내 (Rule 3) | system.txt + `ForbiddenTermsRegistry.quotedList()` | ✅ |
| 캐시 키 v1 → v2 bump | `AiSignalService:74` | ✅ |
| TTL 60분 유지 | `@Value` default 60 | ✅ |

### §5 FE UX — ✅ 92%

**컴포넌트**:

| 설계 컴포넌트 | 파일 | 검증 |
|---|---|---|
| `BeginnerExplanation` | `components/beginner-explanation.tsx` | ✅ |
| `IndicatorInterpretation` | `components/indicator-interpretation.tsx` | ✅ |
| `NewsImpact` | `components/news-impact.tsx` | ✅ ImpactBadge + FreshnessBadge 포함 |
| `WhatToWatch` | `components/what-to-watch.tsx` | ✅ |
| `CollapsibleSection` | `components/collapsible-section.tsx` | ✅ `aria-expanded` + keyboard |
| `FreshnessBadge` (별도 파일) | **news-impact.tsx inline** | 🔵 Changed — 재사용 없어 기능 동등 |

**Graceful Degrade**: 모든 섹션 null/empty 시 `return null` ✅. fallback=true 시 BE 가 v2 필드 null 반환 → 자연 숨김 (🔵 명시적 가드 아닌 행동 동등).

**모바일 기본 접힘 (§5.2)**: `defaultOpen=true` 일괄 — 🟡 Minor Gap (viewport-based 차등 미구현).

### §6 Error Handling — ✅ 100%

- 확장 필드 누락 → 통과 (nullable) ✅
- 타입 오류 → 해당 item drop, 전체 응답 유지 (설계보다 관대 → 🟡 Positive)
- 금지어 스캔에 신규 4 필드 텍스트 포함 ✅

### §7 Security / Guard — ✅ 100%

JWT / RateLimit / LegalGuardFilter / Validator forbidden 스캔 / CI forbidden-terms 전부 기존 체인 재사용. 신규 금지 용어 미추가.

### §8 Test Plan — ⚠️ 55%

| 설계 테스트 | 상태 |
|---|---|
| ResponseValidator v2 옵셔널 단위 | 🔴 Missing |
| ContextAssembler freshness 경계값 단위 | 🔴 Missing |
| AiSignalService E2E IT (extended_response 저장) | 🔴 Missing |
| RawSignal 역직렬화 contract | 🔴 Missing |
| 기존 IT 회귀 (생성자 시그니처 업데이트) | ✅ |
| 프롬프트 dry-run | ⏳ Deferred |
| FE Jest | ⏳ Deferred (프로젝트 전역) |

### §10 Forbidden Terms Guard — ✅ 100%

신규 용어 미추가 (설계 의도대로). 스캔 범위 자동 포함.

### §11.2 Implementation Order — ⚠️ 91%

| Step | 상태 |
|---|:---:|
| 1 Domain 확장 | ✅ |
| 2 V16 + JSONB 매핑 | ✅ |
| 3 ContextAssembler 5건 + freshness | ⚠️ 유닛 테스트 미작성 |
| 4 Prompt v2 | ✅ |
| 5 Validator 확장 | ⚠️ 유닛 테스트 미작성 |
| 6 Service 매핑 + 캐시 v2 | ✅ |
| 7 FE 타입/훅 | ✅ |
| 8 FE 컴포넌트 | 🔵 FreshnessBadge inline |
| 9 패널 리팩터 | ✅ |
| 10 Dry-run 샘플링 | ⏳ Deferred |
| 11 CI / 빌드 | ⚠️ PR 파이프라인 기록 필요 |

---

## 3. Gap 목록

### 🔴 Missing

| # | 항목 | 심각도 |
|---|---|:---:|
| M-1 | ResponseValidator v2 옵셔널 단위 테스트 | Medium |
| M-2 | ContextAssembler freshness 경계값 단위 테스트 | Medium |
| M-3 | AiSignalService E2E IT (extended_response 저장 + 캐시 v2) | Low |
| M-4 | RawSignal 역직렬화 contract 테스트 | Low |
| M-5 | Step 10 dry-run 샘플링 | **Deferred** — 운영 검증 성격 |
| M-6 | 모바일 기본 접힘 차등 | Low |

### 🟡 Added (Positive)

- V16 `WHERE extended_response IS NOT NULL` 부분 인덱스
- `hoursAgo < 0` 미래 시각 방어
- NewsImpact 부분 수용(item drop, 전체 유지)
- FreshnessBadge 한국어 라벨("N시간 전", "N일 전", "오래된 뉴스")
- `buildExtendedResponse` 전체 null 시 Map null 반환 → DB NULL 저장

### 🔵 Changed (경미)

| 항목 | 설계 | 구현 | 영향 |
|---|---|---|:---:|
| FreshnessBadge 파일 분리 | 별도 `freshness-badge.tsx` | news-impact.tsx inline | 낮음 |
| Endpoint 경로 표기 | `/api/v1/ai/signal?ticker=` | `/api/v1/stocks/{ticker}/ai-signal?tf=` | 낮음 (design 오기재) |
| Fallback 섹션 숨김 방식 | 명시적 UI 가드 | BE null + FE null-check 자연 숨김 | 낮음 (동등) |

---

## 4. 권고 Actions

### Immediate (Report 전 권장, non-blocking)

1. **ResponseValidator v2 옵셔널 단위 테스트** 2-3 case (M-1)
2. **ContextAssembler freshness 경계값 단위 테스트** (M-2)

> 위 2건 추가 시 §8 55% → 85%, Overall ≈ 96% 도달.

### Documentation Update

3. Design §4.1 endpoint 경로 실 구현 반영
4. Design §5.3 FreshnessBadge 현 위치 반영
5. Design §6.1 "부분 아이템 drop" 정책 반영
6. Design §10 SQL CI 정책 명확화
7. Step 10 을 "Post-merge 운영 검증" 섹션으로 이동

### Follow-up

8. 모바일 기본 접힘 `defaultOpenMobile?` prop
9. `FreshnessBadge` 파일 추출 (재사용 시)
10. 프로덕션 실 호출 후 토큰/비용 스냅샷
11. 월 Gemini 비용 < 150% 증가 목표 운영 누적 평가

---

## 5. 최종 권고

**Match Rate: 93% → `/pdca report ai-analysis-deepening` 진입 가능.**

차단성 Gap 없음. 설계·구현은 데이터 모델·마이그레이션·프롬프트·API·FE·보안 가드 전 영역에서 정합. 주요 차이는:

- **테스트 공백 (§8)** — 회귀 IT 는 그린 유지. 신규 단위/통합 테스트 4건 부재는 기능 차단이 아닌 품질 보증 공백. 권고 1·2 수행 시 해소.
- **Deferred (Step 10)** — 실 Gemini 호출 필요로 운영 검증 이관. signal-accuracy Step 9 선례와 동일.
- **경미 Changed 3건** — 모두 기능 동등, 문서 갱신만 필요.

Plan §4.1 DoD 7 항목 중 6 항목 충족. "AAPL/TSLA/NVDA 수동 검증" 은 Report 단계에서 Step 10 운영 검증으로 명시.
