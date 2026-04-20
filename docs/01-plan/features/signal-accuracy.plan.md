# signal-accuracy Planning Document

> **Summary**: AI 시그널의 과거 방향 정합도(hit rate)를 측정·공개하여, 분석 신뢰도를 정량화하고 프롬프트/모델 튜닝의 기준선을 확보한다.
>
> **Project**: ai-stock-advisor
> **Version**: v0.1.1 (예정, Phase 5 첫 기능)
> **Author**: wonseok-han
> **Date**: 2026-04-20
> **Status**: Draft

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | AI 시그널이 "좋아진 것 같다" 수준으로만 평가되어, 프롬프트·컨텍스트·모델 변경이 개선인지 개악인지 판단 불가. 사용자도 분석 신뢰도를 가늠할 기준이 없음. |
| **Solution** | 이미 감사 저장되고 있는 `ai_signal_audit` 에 N일 후 실제 가격을 매칭해 **방향 정합도(hit rate)** 를 집계. API 로 노출 + AI 분석 카드에 "지난 30일 분석 정합도 XX%" 배지. |
| **Function/UX Effect** | 사용자: 분석 카드에서 과거 정합도 확인 → 신뢰도 스스로 판단. 운영자: 프롬프트/컨텍스트 변경의 효과를 수치로 비교 (A/B 튜닝 기반). |
| **Core Value** | **측정 가능한 AI**. 이후 컨텍스트 확장·멀티모델 앙상블 같은 고도화가 "좋아졌다"를 증명 가능한 형태로 진행됨. |

---

## 1. Overview

### 1.1 Purpose

AI 시그널의 과거 방향 정합도를 측정·공개한다. 목적은 두 가지:
1. **사용자 신뢰도 투명화** — "AI 의 단기 방향 제시가 최근 30일 동안 얼마나 맞았는지" 를 그대로 보여줌.
2. **내부 튜닝 기준선** — 프롬프트 개정·컨텍스트 추가·모델 교체의 효과를 정량 비교.

### 1.2 Background

- 현재 `ai_signal_audit` 테이블(V4)에 모든 AI 시그널 응답이 영구 저장 중. `context_payload.quote.price` 에 시그널 시점 가격이 포함되어 있어 **백필 가능**.
- 캔들 DB(V8, Phase 4.5.1) 에 일봉 OHLCV 장기 보관 중 → N일 후 종가 조회 가능.
- Phase 5+ 방향 미확정 상태에서 "AI 분석 고도화" 를 선택. 측정 없이 컨텍스트 확장/앙상블을 하면 개선 여부 판단 불가 → **측정 인프라 먼저**.

### 1.3 Related Documents

- 초기 기획: [`docs/planning/05-ai-strategy.md`](../../planning/05-ai-strategy.md) §5.3 용도 A, §5.6 품질 검증 루프
- 법적 원칙: [`docs/planning/07-legal-compliance.md`](../../planning/07-legal-compliance.md) §7.2 (금지 용어), §7.7 (수익화 재검토)
- 관련 구현: [`apps/api/src/main/resources/db/migration/V4__phase2_ai_signal_audit.sql`](../../../apps/api/src/main/resources/db/migration/V4__phase2_ai_signal_audit.sql), [`V8__candles.sql`](../../../apps/api/src/main/resources/db/migration/V8__candles.sql)
- 과거 아카이브: [`docs/archive/2026-04/phase2-rag-pipeline/`](../../archive/2026-04/phase2-rag-pipeline/)

---

## 2. Scope

### 2.1 In Scope

- [ ] **스키마**: `ai_signal_evaluation` 테이블 (audit_id FK, window_days, price_at_signal, price_at_window_end, actual_change_pct, predicted_direction, actual_direction, hit, evaluated_at)
- [ ] **백필 스크립트**: 기존 audit 레코드 중 평가 window 경과한 건에 대해 일괄 평가 생성 (one-shot Gradle task 또는 admin 엔드포인트)
- [ ] **스케줄러**: 매일 1회 (UTC 06:00, NY close 후), window 경과한 미평가 audit 을 찾아 캔들 DB 에서 종가 조회 → 평가 저장
- [ ] **평가 로직**: 방향 매핑(STRONG_BUY=+2 ... STRONG_SELL=-2), change_pct 기반 actual_direction 계산, hit 판정 (완곡한 룰: 방향 대략 일치 시 hit, Neutral 은 ±2% 내)
- [ ] **집계 API**: `GET /api/v1/ai/accuracy?window=7|30&ticker=X` → `{ sampleSize, hitRate, bySignal: {...}, evaluatedThrough }`
- [ ] **FE UI**: AI 분석 카드 하단에 "지난 30일 분석 방향 정합도 XX% (N건 기준)" 배지. 클릭 → 간단한 툴팁/모달로 by_signal 브레이크다운. **"정확도"·"예측"** 단어 금지 → **"정합도"·"방향 일치율"** 사용.
- [ ] **Micrometer 메트릭**: `ai_signal_evaluation_total{window,hit}`, `ai_signal_hit_rate{window}` 게이지
- [ ] **면책 강화**: 배지 옆 `i` 아이콘 → "과거 성과는 미래 수익을 보장하지 않습니다. 본 정합도는 내부 튜닝 지표이며 투자 판단 근거가 아닙니다."

### 2.2 Out of Scope

- 수익률 시뮬레이션 / 가상 매매 — 유사투자자문업 경계에 매우 근접, Phase 5+ 별도 검토
- 사용자별 개인화 정합도 — 개인 포트폴리오 기반 조언 금지 원칙
- Confidence 보정(calibration) — v0.2 이후 튜닝 단계에서 별도 검토
- 멀티 모델 앙상블 투표 — A 다음 단계(B) 에서 진행
- 실시간 평가 (시그널 발행 즉시 카운트다운) — 배치만으로 충분
- **종목별 개별 시그널 공개 이력** — 신뢰도 하락 리스크 + 유사투자자문 해석 여지. 집계만 공개.

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | `ai_signal_evaluation` 테이블 및 Flyway V15 마이그레이션 | High | Pending |
| FR-02 | 방향 매핑 + hit 판정 도메인 서비스 (`SignalOutcomeEvaluator`) | High | Pending |
| FR-03 | 일일 스케줄러: 7d/30d window 미평가 audit 조회 → 캔들 조회 → 평가 저장 | High | Pending |
| FR-04 | 백필 admin 엔드포인트 (`POST /api/admin/ai/backfill-evaluation?window=30`) — Basic Auth | High | Pending |
| FR-05 | 집계 API `GET /api/v1/ai/accuracy?window=30&ticker=?` — 캐시 1h | High | Pending |
| FR-06 | FE: AI 분석 카드 배지 + 툴팁 (정합도·샘플 수·면책) | Medium | Pending |
| FR-07 | 금지 용어 체크: "정확도"/"예측"/"적중" 등 FE/백엔드 메시지에서 배제, 대신 "정합도"/"방향 일치율" | High | Pending |
| FR-08 | Micrometer 메트릭 + 기존 `/actuator/prometheus` 노출 | Medium | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| 정확성 | 백필 결과가 수동 검산 3건과 일치 | AAPL/TSLA/NVDA 샘플 수동 검증 |
| 성능 | 집계 API p95 < 150ms (캐시 히트 시 < 20ms) | Actuator metrics |
| 비용 | 캔들 조회는 DB 만 사용, 외부 API 호출 0 | 스케줄러 로그 검증 |
| 보안 | 백필 admin 엔드포인트 Basic Auth + CI 검증 | Spring Security 통합 테스트 |
| 법적 | "예측/권유/추천" 용어 0건 (기존 forbidden-terms CI 에 신규 3건 추가) | `.github/workflows/forbidden-terms.yml` |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] V15 마이그레이션 로컬·Testcontainers 통과
- [ ] 스케줄러 dry-run 으로 백필 후 수동 검산 3종목 모두 ±1% 오차 내 일치
- [ ] 집계 API 응답이 샘플 수·hit rate 반환, 샘플 수 < 5 일 때 "샘플 부족" 플래그 반환
- [ ] AI 분석 카드에 배지 노출, 샘플 수 < 5 이면 배지 숨김 (오해 방지)
- [ ] `make check` 통과 (FE tsc+lint + BE Gradle check)
- [ ] forbidden-terms CI 통과

### 4.2 Quality Criteria

- [ ] 백엔드 단위 테스트: `SignalOutcomeEvaluator` 방향 매핑·hit 판정 케이스 커버 (Buy·Neutral·Sell 경계)
- [ ] 통합 테스트: Testcontainers 로 스케줄러 1회 실행 후 평가 레코드 검증
- [ ] FE: 배지·툴팁 렌더링 스냅샷 테스트 (Jest/RTL)
- [ ] Zero lint error

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| 초기 샘플 부족 (Beta 직후, audit 레코드 < 20) → hit rate 극단값 | High | High | 샘플 수 < 5 시 배지 숨김, API 응답에 `sampleSizeSufficient: false` 반환. 수치 대신 "평가 누적 중" 표시. |
| 정합도가 낮게 나와 서비스 신뢰도 하락 | High | Medium | **이게 핵심 가치**. 낮아도 투명하게 공개. 대신 "단기 방향 시그널은 확률적 판단 보조일 뿐" 문구로 기대치 조정. |
| 유사투자자문 해석 경계 (hit rate 를 "권유 정확도" 로 오독) | High | Low | 용어 통제 (정합도·일치율), 면책 고지, 종목별 개별 이력 비공개. 집계만 공개. |
| 캔들 DB 에 종가 누락 (주말·휴일 이후 shift 누락) | Medium | Medium | 시그널 시점 + window_days 기준이 아닌 **다음 거래일 종가 우선**으로 로직화. 휴일 처리는 캔들 DB 기준 asc 탐색. |
| Confidence 편향 — Low confidence 시그널의 hit rate 가 Buy·Sell 평균을 흐림 | Low | Medium | `bySignal` 외에 `byConfidenceBucket` (0.5 미만 / 이상) 도 노출 (향후 v0.2) — 우선은 bySignal 만. |
| 백필이 audit 전체를 한 번에 처리 시 DB 부하 | Medium | Low | 배치 1000 건 단위 커밋, 백필 엔드포인트는 admin only + idempotent (`ON CONFLICT DO NOTHING`). |

---

## 6. Architecture Considerations

### 6.1 Project Level Selection

| Level | Characteristics | Recommended For | Selected |
|-------|-----------------|-----------------|:--------:|
| **Starter** | Simple structure | 정적 사이트 | ☐ |
| **Dynamic** | Feature-based modules, BaaS | 풀스택 웹앱 (현 프로젝트) | ☑ |
| **Enterprise** | Strict layer separation | 고트래픽 | ☐ |

### 6.2 Key Architectural Decisions

| Decision | Options | Selected | Rationale |
|----------|---------|----------|-----------|
| 평가 데이터 저장 위치 | audit 컬럼 추가 / 별도 테이블 | **별도 테이블** (`ai_signal_evaluation`) | audit 은 append-only 감사 로그 원칙 유지, 평가는 별도 관심사 + window 별 다중 레코드 가능 (7d/30d) |
| 스케줄 방식 | `@Scheduled` / Quartz / 외부 | **`@Scheduled` (Spring)** | 기존 방식 일관 (알림 스케줄러와 동일) |
| window 길이 | 1/7/14/30/90 | **7d, 30d 만 시작** | UX 단순성, 단기(1~2주) 시그널 성격 대응. 90d 는 samples 충분해진 후 검토 |
| hit 판정 룰 | strict direction / 확률적 버킷 | **strict 5-class → 3-class 매핑** | STRONG_BUY/BUY → UP, NEUTRAL → FLAT, SELL/STRONG_SELL → DOWN. actual 은 ±2% 기준 3-class. |
| FE 배지 표현 | "정확도" / "정합도" / "방향 일치율" | **"분석 방향 정합도"** | 법적 리스크 최소 + 직관적 |
| 백필 트리거 | CLI Gradle task / admin HTTP | **admin HTTP (`POST /api/admin/...`)** | 원격 실행 가능 (프로덕션), Basic Auth 기존 admin 구조 재사용 |

### 6.3 Clean Architecture Approach

```
Selected Level: Dynamic

Backend (apps/api/src/main/java/com/aistockadvisor/ai):
├── domain/
│   └── SignalOutcome.java (record: window, hit, predicted, actual, changePct)
│   └── SignalEvaluation.java (record)
├── infra/
│   └── AiSignalEvaluationEntity.java
│   └── AiSignalEvaluationRepository.java
├── service/
│   └── SignalOutcomeEvaluator.java (pure domain logic: mapping + hit)
│   └── SignalEvaluationService.java (audit 조회 + 캔들 조회 + 저장)
│   └── SignalAccuracyService.java (집계 + 캐시)
│   └── SignalEvaluationScheduler.java (@Scheduled)
└── web/
    └── SignalAccuracyController.java (GET /api/v1/ai/accuracy)
    └── AdminEvaluationController.java (POST /api/admin/ai/backfill-evaluation)

Frontend (apps/web/src):
├── features/stock-detail/
│   └── components/
│       └── ai-accuracy-badge.tsx
│       └── ai-accuracy-tooltip.tsx
└── services/
    └── fetch-ai-accuracy.ts (React Query hook)
```

---

## 7. Convention Prerequisites

### 7.1 Existing Project Conventions

- [x] `CLAUDE.md` 코딩 컨벤션 (FE kebab-case, BE PackageName)
- [x] Flyway 마이그레이션 규칙 (V{n}__{name}.sql, 순차 번호)
- [x] forbidden-terms CI (Level 4 가드)
- [x] 면책 고지 컴포넌트 (`disclaimer-footer`)
- [x] 환경변수 prefix 규칙 (`NEXT_PUBLIC_`, 서버 전용 prefix)

### 7.2 Conventions to Define/Verify

| Category | Current State | To Define | Priority |
|----------|---------------|-----------|:--------:|
| Signal→Direction 매핑 | 없음 | `SignalOutcomeEvaluator` 내부 상수 | High |
| Actual change threshold | 없음 | `±2%` (설정값 `app.ai.evaluation.flat-threshold-pct`) | High |
| Forbidden terms 추가 | 있음 | "정확도"/"예측"/"적중률" UI·메시지 배제 | High |

### 7.3 Environment Variables Needed

| Variable | Purpose | Scope | To Be Created |
|----------|---------|-------|:-------------:|
| `APP_AI_EVALUATION_ENABLED` | 스케줄러 on/off | Server | ☑ |
| `APP_AI_EVALUATION_WINDOWS` | "7,30" 기본 | Server | ☑ |
| `APP_AI_EVALUATION_FLAT_THRESHOLD_PCT` | Neutral 판정 기준 | Server | ☑ |

### 7.4 Pipeline Integration

해당 없음 (9-phase pipeline 미적용, PDCA 로 직행).

---

## 8. Next Steps

1. [ ] `/pdca design signal-accuracy` — 상세 설계 (스키마 DDL, API 계약, UI 와이어)
2. [ ] `/pdca do signal-accuracy` — Step 단위 구현
3. [ ] `/pdca analyze signal-accuracy` — Gap 분석

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-20 | Initial draft (A: 신뢰도 입증 — 측정 기반 고도화) | wonseok-han |
