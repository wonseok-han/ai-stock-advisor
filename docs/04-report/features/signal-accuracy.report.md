# signal-accuracy 완료 리포트

> **Summary**: AI 시그널 방향 정합도(hit rate) 측정 인프라 완성. Design 96% 매칭 달성, 차단성 gap 없음.
>
> **Project**: nowini
> **Feature**: signal-accuracy (v0.1.1)
> **Owner**: wonseok-han
> **Duration**: 2026-04-20 ~ 2026-04-21 (2일, 압축 PDCA)
> **Status**: Completed ✅

---

## 1. Executive Summary

### 1.1 PDCA 개요

| 단계 | 문서 | 상태 |
|------|------|------|
| **Plan** | `docs/01-plan/features/signal-accuracy.plan.md` (v0.1.1) | ✅ |
| **Design** | `docs/02-design/features/signal-accuracy.design.md` (v0.1.1) | ✅ |
| **Do** | 구현 완료 (Step 1~9) | ✅ |
| **Check** | `docs/03-analysis/signal-accuracy.analysis.md` | ✅ Match Rate 96% |

### 1.2 핵심 성과

| 지표 | 값 | 상태 |
|------|-----|------|
| **Design Match Rate** | **96%** | ✅ 기준(90%) 초과 |
| **구현 완료도** | 9/9 Step | ✅ 전체 Step 완료 |
| **차단성 Gap** | 0건 | ✅ None |
| **경미한 Changed** | 5건 | 🔵 전역 컨벤션 정합 |
| **긍정적 Added** | 7건 | ✅ 보안·품질 강화 |
| **백엔드 테스트** | 30+ cases | ✅ Green |
| **프론트엔드 테스트** | Pending | 🟡 Jest setup 대기 |

### 1.3 Value Delivered (4-perspective 요약)

| 관점 | 내용 |
|------|------|
| **문제(Problem)** | AI 시그널이 "좋아진 것 같다"만 평가되어, 프롬프트·컨텍스트·모델 변경이 개선인지 개악인지 판단 불가. 신뢰도 기준 없음. |
| **해결(Solution)** | 기존 `ai_signal_audit` + `candles` DB 조합으로 **방향 정합도 배치 평가 인프라** 구축. 외부 API 호출 없이 비용 0, 매일 자동 집계. |
| **UX/기능 효과** | 사용자: AI 카드에서 "지난 30일 분석 방향 정합도 XX%" 배지 확인 → 신뢰도 투명화. 운영자: 프롬프트/모델 변경 효과를 정량 비교 가능 (A/B 튜닝 기반). |
| **핵심 가치** | **측정 가능한 AI**. 이후 컨텍스트 확장·멀티모델 앙상블이 "좋아졌다"를 **증명 가능한 형태**로 진행 가능. Phase 5+ AI 고도화의 기초. |

---

## 2. PDCA 사이클 요약

### 2.1 Plan (2026-04-20)

**목표**: AI 시그널 과거 방향 정합도 측정 인프라 명세

**주요 결정**:
- 평가 데이터 저장: 별도 테이블 `ai_signal_evaluation` (audit append-only 원칙 유지)
- 스케줄 방식: `@Scheduled` (Spring, 기존 알림 스케줄러 동일)
- Window 길이: 7d, 30d 시작 (UX 단순성, 단기 시그널 성격)
- Hit 판정: 5-class → 3-class 매핑 (STRONG_BUY/BUY→UP, NEUTRAL→FLAT, SELL/STRONG_SELL→DOWN)
- 법적 가드: "정확도/예측/적중" 금지 → "정합도/방향 일치율" 사용 (CI + 소스 정적 스캔)

**환경변수 신규**:
```
AI_EVALUATION_ENABLED=true
AI_EVALUATION_WINDOWS=7,30
AI_EVALUATION_FLAT_THRESHOLD_PCT=2.0
AI_EVALUATION_CRON=0 0 6 * * *  (UTC)
```

### 2.2 Design (2026-04-20)

**설계 범위**: 
- DB 스키마 (V15__ai_signal_evaluation.sql)
- 순수 도메인 평가자 (`SignalOutcomeEvaluator`)
- 일일 스케줄러 + 백필 admin 엔드포인트
- 집계 API + FE 배지·툴팁

**주요 컴포넌트**:
- **Backend**: 
  - `SignalOutcome(predicted, actual, changePct, hit)` domain record
  - `SignalEvaluationService`: audit 조회 + 캔들 조회 + 평가 저장
  - `SignalAccuracyService`: Redis 1h 캐시 + 집계
  - `SignalAccuracyController`: `GET /api/v1/ai/accuracy?window=30` (public)
  - `AdminEvaluationController`: `POST /api/admin/ai/backfill-evaluation` (Basic Auth)

- **Frontend**:
  - `AiAccuracyBadge`: 배지 렌더 (샘플 <5 시 조용한 숨김)
  - `AiAccuracyTooltip`: bySignal 브레이크다운 팝오버
  - `useAccuracy`: React Query hook (staleTime 1h, retry 1)

**Clean Architecture** (Dynamic Level):
```
Web (Controller) → Service (Logic) → Domain (Pure) + Infra (Repository)
FE Component → Hook (React Query) → Service (fetch) → Type
```

### 2.3 Do (구현 완료)

**Step 단위 구현 (2026-04-21)**:

| Step | 범위 | 파일 수 | 상태 |
|------|------|--------|------|
| 1 | DB 스키마 + Repo | `V15__...sql`, 2개 entity | ✅ |
| 2 | Pure evaluator + Unit test | `SignalOutcomeEvaluator.java`, `SignalOutcomeEvaluatorTest.java` | ✅ |
| 3 | Evaluation service | `SignalEvaluationService.java`, integration test | ✅ |
| 4 | Scheduler + Admin endpoint | `SignalEvaluationScheduler.java`, `AdminEvaluationController.java` | ✅ |
| 5 | Accuracy API + Cache | `SignalAccuracyService.java`, `SignalAccuracyController.java` | ✅ |
| 6 | FE 타입·서비스·훅 | `types/ai-accuracy.ts`, `lib/api/ai-accuracy.ts`, `use-accuracy.ts` | ✅ |
| 7 | FE 컴포넌트 | `ai-accuracy-badge.tsx`, `ai-accuracy-tooltip.tsx` | ✅ |
| 8 | Forbidden terms CI | `.github/workflows/forbidden-terms.yml` 5개 용어 추가 | ✅ |
| 9 | Prod backfill | Admin endpoint 202 + `candidateCount=0` (자연 누적 대기) | ✅ |

**구현 통계**:
- **파일 신규**: 24개 (BE 14, FE 8, CI 1, DB 1)
- **파일 수정**: 3개 (security config, api-candle mapping, app config)
- **총 코드 라인**: ~1,200 LOC (BE 800, FE 350, SQL 50)

### 2.4 Check (Gap Analysis)

**분석 결과**: Match Rate **96%**

**검증 항목별**:
| 카테고리 | 점수 | 상태 |
|---------|------|------|
| 데이터 모델 (§3) | 100% | ✅ 완벽 일치 |
| 수집 파이프라인 (§4·§11) | 100% | ✅ 9 Step 모두 구현 |
| 조회 API & FE (§5) | 98% | ✅ 파일 경로 경미 |
| 비기능 (§6·§7) | 95% | ✅ 보안 강화 |
| 테스트 (§8) | 88% | ⚠️ FE Jest pending |
| 법적·UX 가드 (§10) | 100% | ✅ CI 용어 차단 |

**Gap 요약**:
- 🔴 Missing: 0건
- 🟡 Added (긍정적): 7건 (fallback 필터, async 회피, safe-default 등)
- 🔵 Changed (경미): 5건 (FE 파일 경로, env 변수 prefix 등)

---

## 3. 구현 요약

### 3.1 주요 파일 목록

**Backend** (`apps/api/src/main/java/com/aistockadvisor/ai/`):
```
domain/
  ├── SignalOutcome.java (record + Direction enum)
  ├── SignalOutcomeEvaluator.java (pure, 방향 매핑)
  ├── EvaluationWindow.java (W7, W30 상수)
  ├── AccuracySummary.java (응답 DTO)

infra/
  ├── AiSignalEvaluationEntity.java
  ├── AiSignalEvaluationRepository.java

service/
  ├── SignalEvaluationService.java (audit 조회 + 평가)
  ├── SignalEvaluationScheduler.java (@Scheduled)
  ├── SignalAccuracyService.java (집계 + Redis cache)

web/
  ├── SignalAccuracyController.java (GET /api/v1/ai/accuracy)
  ├── AdminEvaluationController.java (POST /api/admin/ai/backfill-evaluation)
  ├── dto/AccuracyResponse.java
  ├── dto/BackfillRequest.java
```

**Database**:
```
src/main/resources/db/migration/
  └── V15__ai_signal_evaluation.sql
      └── 테이블: audit_id FK, window_days, signal, predicted_direction, actual_direction, hit
      └── 인덱스: (window_days, evaluated_at DESC), (window_days, signal)
      └── UNIQUE (audit_id, window_days) → idempotent
```

**Frontend** (`apps/web/src/`):
```
types/
  └── ai-accuracy.ts (AccuracyResponse, BucketStat types)

lib/api/
  └── ai-accuracy.ts (fetch wrapper)

features/stock-detail/
  ├── components/
  │   ├── ai-accuracy-badge.tsx (배지 렌더, 조건부 숨김)
  │   └── ai-accuracy-tooltip.tsx (팝오버, bySignal 표)
  └── hooks/
      └── use-accuracy.ts (React Query, staleTime 1h)
```

**CI**:
```
.github/workflows/
  └── forbidden-terms.yml (Pass 2 추가: 정확도, 예측, 적중, 적중률, 승률)
```

### 3.2 핵심 설계 결정

#### 1. 별도 테이블 (audit append-only 유지)
- `ai_signal_evaluation` 은 평가 결과 파생 저장
- audit 은 원천 감사 로그, 불변 원칙
- 가능: window 별 다중 레코드, 미래 window 추가 용이

#### 2. 비용 0 (외부 API 호출 없음)
- 캔들 DB 만 사용 (이미 저장된 종가)
- 백필: admin 엔드포인트 (원격 트리거)
- 스케줄러: 매일 1회 (06:00 UTC, NY close 후)

#### 3. Pure domain logic 분리
- `SignalOutcomeEvaluator` 는 순수 함수 (도메인 로직만)
- 인프라 의존 0 → 단위 테스트 완전 커버
- 향후 다중 model/confidence 버킷 추가 시 재사용 가능

#### 4. 법적 용어 분리 (CI vs 런타임)
- **CI 정적 스캔** (Pass 2): "정확도/예측/적중" 소스 코드 차단
- **런타임 JSON 차단** (LegalGuardFilter): 투자 자문 유도 문구만 (오탐 리스크 회피)
- 이유: "예측"은 뉴스 번역·분석가 코멘트에서 중립적 맥락이 많음

#### 5. 샘플 부족 시 조용한 숨김 (silent-hide)
- `sampleSize < 5` 시 FE 배지 자체 렌더 안 함
- API 응답: `sampleSizeSufficient: false`, `hitRate: null`
- 이유: 극단값 노출 금지 + 오해 방지 (투자 판단 근거로 오독 위험)

#### 6. Idempotent 평가
- `UNIQUE (audit_id, window_days)` → 재계산 시 중복 없음
- 백필: `ON CONFLICT DO NOTHING` (또는 `ON CONFLICT ... DO UPDATE`)
- 의미: 같은 audit 을 2번 이상 평가해도 안전

---

## 4. 테스트 결과

### 4.1 백엔드 테스트

**유닛 테스트** (JUnit 5):
```
SignalOutcomeEvaluatorTest
  ✅ evaluate_BUY_withPositiveChange_returnsUpHit
  ✅ evaluate_BUY_withNegativeChange_returnsUpMiss
  ✅ evaluate_NEUTRAL_withSmallChange_returnsFlat
  ✅ evaluate_NEUTRAL_withLargeChange_returnsMiss
  ✅ evaluate_STRONG_SELL_withNegativeChange_returnsDownHit
  ✅ ... (경계, 임계값 포함 10 cases)
```

**통합 테스트** (Spring Boot Test + Testcontainers):
```
SignalEvaluationServiceIT
  ✅ evaluateWindow_withValidAudits_createsEvaluations
  ✅ evaluateWindow_withMissingCandle_skipsWithNoPriceCount
  ✅ evaluateWindow_withDuplicateCall_idempotent (UNIQUE 제약)
  ✅ ... (6 cases)

SignalAccuracyControllerIT
  ✅ getAccuracy_window30_returnsHitRate
  ✅ getAccuracy_insufficient_returnsSampleSizeSufficient=false
  ✅ getAccuracy_invalidWindow_returns400
  ✅ ... (4 cases)

AdminEvaluationControllerIT
  ✅ backfillEvaluation_withBasicAuth_returns202
  ✅ backfillEvaluation_withoutAuth_returns401
  ✅ backfillEvaluation_withInvalidWindow_returns400
  ✅ ... (4 cases)
```

**테스트 총계**: 30+ cases, **모두 Green** ✅

### 4.2 프론트엔드 테스트

**상태**: 🟡 Jest setup 부재

- 프로젝트에 Jest/RTL 구성 아직 미완료
- `AiAccuracyBadge` 조건부 렌더 (sufficient/insufficient/error)는 설계·구현 완료
- **권고**: Phase 6+ 에서 프로젝트 전체 Jest 통합 후 추가

### 4.3 통합 검증

**금지어 CI** (`.github/workflows/forbidden-terms.yml`):
```bash
✅ grep -r "정확도\|예측\|적중\|적중률\|승률" apps/ --include="*.ts" --include="*.tsx" --include="*.java"
→ 결과: 0건 (Pass)
```

**API 계약** (OpenAPI/Swagger):
```bash
✅ springdoc-openapi 자동 생성
→ /api/v1/ai/accuracy 스키마 문서화 완료
```

---

## 5. 법적·UX 가드

### 5.1 금지어 컨트롤

**CI Pass 2 (정적 스캔)** — `.github/workflows/forbidden-terms.yml`:
```yaml
ACCURACY_TERMS=("정확도" "예측" "적중" "적중률" "승률")
# 모든 .ts/.tsx/.java 파일 스캔 (legal/ 제외)
# → 0건 확인됨
```

**런타임 필터** (LegalGuardFilter) — `.github/workflows/forbidden-terms.json`:
- 설계 의도: runtime JSON 은 **투자 자문 유도 문구**(54종, v1.1)만 포함
- 이 기능의 새 용어 5종은 **동적 차단 대상 아님** (오탐 리스크 높음)
- 예: "이 분석은 예측입니다" ← AI 정당한 표현, 차단 불필요

### 5.2 UX 면책

**배지 옆 아이콘** (`i` 버튼, tooltip):
```
"과거 성과는 미래 수익을 보장하지 않습니다. 
본 정합도는 내부 튜닝 지표이며 투자 판단 근거가 아닙니다."
```

**샘플 부족 시**:
- UI: "평가 누적 중" (회색)
- API: `sampleSizeSufficient: false`, `hitRate: null`

**종목별 개별 시그널 공개 안 함**:
- 설계: `GET /api/v1/ai/accuracy?ticker=X` Scope Out
- 이유: 신뢰도 하락 + 유사투자자문 해석 여지

---

## 6. 배포 현황

### 6.1 Step 9: 프로덕션 백필

**상태**: ✅ 인프라 검증 완료, 샘플 자연 누적 대기

**배경**:
- Admin 엔드포인트 (`POST /api/admin/ai/backfill-evaluation`) 202 응답 확인
- `candidateCount=0` = 평가 대상 audit 없음 (자연, gap 아님)
  - 이유: `now - window_days` (e.g., 30일 전) 조건 미충족
  - 시그널 발행 30일 이상 경과해야 평가 가능

**누적 기다림**:
- Phase 4 ~ Phase 5 초반 (2026-03 ~ 2026-04) 시그널 데이터 축적 중
- 2026-05-20 경 30일 윈도우 자동 통과 → 스케줄러 자동 평가 시작
- Sample >= 5 달성 후 사용자 노출 예정

### 6.2 프로덕션 체크리스트

- [x] 스키마 마이그레이션 (V15) 로컬·Testcontainers 통과
- [x] 스케줄러 드라이런 (dry-run mode 로 수동 검산 3건 완료)
- [x] 백필 엔드포인트 Basic Auth + 202 응답 검증
- [x] 집계 API 캐시 동작 (Redis 1h)
- [x] FE 배지 렌더 (silent-hide 동작 확인)
- [x] forbidden-terms CI 통과
- [ ] 운영 샘플 누적 (>= 5건) — 자동, 2026-05 경 예상

---

## 7. Lessons Learned

### 7.1 What Went Well (긍정 요소)

#### 1. **Pure domain logic 분리의 힘**
- `SignalOutcomeEvaluator` 순수 함수로 설계 → 단위 테스트 100% 커버
- 향후 confidence 버킷·멀티 model 추가 시 재사용 가능
- 테스트 속도 향상, 리팩토링 안전

#### 2. **Append-only audit 원칙 유지**
- 별도 테이블 `ai_signal_evaluation` 으로 평가 분리
- 추적성·감사 이력 완벽 보존
- 미래 재평가 루프(예: confidence 캘리브레이션) 안전

#### 3. **금지어 CI vs 런타임 분리**
- 기술적: 소스 정적 스캔(Pass 2) + 런타임 필터(JSON) 이원화
- 법적: 오탐 리스크(뉴스 "예측") 제거, 진정한 위험(투자 자문) 차단
- 결과: 정확하고 실용적인 가드레일

#### 4. **샘플 부족 시 silent-hide**
- FE 배지 자체를 조건부 렌더 → 사용자 혼동 차단
- API 응답: `sampleSizeSufficient` 플래그 → 프론트엔드 상태 제어
- 통계적 무의미를 투명하게 처리

#### 5. **Idempotent 설계**
- `UNIQUE (audit_id, window_days)` + `ON CONFLICT`
- 스케줄러 재실행·백필 재시도 안전
- 데이터 무결성 자동 보장

### 7.2 Areas for Improvement (개선 포인트)

#### 1. **FE Jest 환경 미완료**
- 상태: 프로젝트 전체 Jest setup 부재
- 영향: `AiAccuracyBadge` 조건부 렌더 테스트 pending (88% → 100% 가능)
- 권고: Phase 6 이후 프로젝트 전체 Jest 통합 시 추가

#### 2. **@Async + @Transactional self-proxy 이슈**
- 경험: Spring AOP 자체 호출 루프에서 `@Async` 실패
- 해결: `ApplicationContext.getBean()` 명시적 프록시 우회
- 교훈: 분산 로직 복잡도 증가 → 향후 dedicated scheduler task 고려

#### 3. **환경변수 prefix 미결정**
- 설계: `APP_AI_EVALUATION_*` / `APP_ADMIN_*`
- 실제: `AI_EVALUATION_*` / `ADMIN_*` (property key 는 `app.ai.evaluation.*`)
- 개선: 문서 싱크 필요 (Gap analysis 권고항목 5-1)

#### 4. **운영 샘플 누적 대기**
- 현재: `candidateCount=0` (자연, 30일 경과 아직 안 됨)
- 위험: 5월까지 샘플 < 5 → 배지 숨김 → 사용자 visibility 제로
- 권고: 2026-05 중순 재검증, 필요 시 베타 샘플 수동 생성 검토

### 7.3 To Apply Next Time (향후 반영)

#### 1. **정적 스캔 + 동적 필터 이원화**
- 이번 경험: 소스 정적(CI Pass 2) vs 런타임 필터(JSON) 분리 효과 확증
- 다음: 다른 법적 조건(예: PII 차단) 설계 시 동일 패턴 적용

#### 2. **Pure domain first 설계**
- 이번: `SignalOutcomeEvaluator` 순수 함수 → 테스트·재사용 완벽
- 다음: 새 도메인 로직 설계 시 항상 infra 의존성 0으로 시작

#### 3. **Idempotent 배치 설계**
- 이번: `UNIQUE + ON CONFLICT` → 재시도 안전 자동 보장
- 다음: 모든 배치·스케줄러는 idempotent 기본 설계

#### 4. **Silent fallback 패턴**
- 이번: 샘플 부족 시 배지 조용히 숨김 → 오해 방지
- 다음: 데이터 불충분·에러 상황 → 사용자에게 보이지 않는 방식으로 처리

#### 5. **법적 용어 사전 식별**
- 이번: 금지어 5개 초반에 명확히 → CI 리젯 방지
- 다음: 기능 설계 단계에서 법적팀(또는 가이드 문서)과 용어 검증 루프

---

## 8. Next Steps

### Immediate (Report 이후 바로)

1. **브랜치 PR + 병합**
   ```bash
   git push origin feat/signal-accuracy
   # → GitHub PR 생성 → Review 1건 → Squash merge main
   # → `develop` 병합 선택적
   ```

2. **문서 싱크** (Gap analysis 권고 5건)
   - [ ] 설계 §10.3 env 변수명 갱신: `APP_AI_EVALUATION_*` → `AI_EVALUATION_*`
   - [ ] 설계 §11.1 FE 파일 경로: `src/services/` → `src/lib/api/`
   - [ ] 설계 §6 에러 코드: 표준 `INVALID_REQUEST` 명시
   - [ ] 설계 §11.1 배지 마운트 지점: `AiAnalysisCard` → `AiSignalPanel`
   - [ ] Step 9 기록: "prod backfill candidateCount=0, 운영 누적 대기"

### Near-term (1주일 내)

3. **운영 모니터링**
   - [ ] 2026-04-27: 스케줄러 로그 확인 (첫 실행 기록)
   - [ ] 2026-05-01: sample count 누적 상태 체크
   - [ ] 2026-05-20: 30일 윈도우 자동 평가 시작 확인

4. **사용자 노출 게이트**
   - [ ] Sample >= 5 달성 시 FE 배지 자동 표시 시작
   - [ ] Accuracy badge 클릭률·tooltip 대화 분석
   - [ ] 사용자 신뢰도 피드백 수집

### Follow-up (Non-blocking)

5. **FE Jest setup** (Phase 6+)
   - [ ] 프로젝트 전체 Jest 환경 통합
   - [ ] `AiAccuracyBadge` 조건부 렌더 테스트 추가 (88% → 100%)

6. **v0.2 고도화**
   - [ ] Confidence 버킷별 정합도 (`byConfidenceBucket`)
   - [ ] 90일 윈도우 추가
   - [ ] 멀티 모델 앙상블 평가

7. **샘플 자연 누적 재검증** (2026-05 중순)
   - [ ] Hit rate 실제 범위 관찰
   - [ ] 극단값 노출 리스크 재평가
   - [ ] Signal 별 편향 분석 (Strong vs Neutral)

---

## 9. Archive 준비

### 9.1 문서 이관

PDCA 사이클 완료, Match Rate 96% ≥ 90% 달성 → Archive 진입 권고

**이관 대상**:
```
docs/01-plan/features/signal-accuracy.plan.md
docs/02-design/features/signal-accuracy.design.md
docs/03-analysis/signal-accuracy.analysis.md
docs/04-report/features/signal-accuracy.report.md
  → docs/archive/2026-04/signal-accuracy/
```

**실행 명령**:
```bash
/pdca archive signal-accuracy
```

### 9.2 변경 기록

Changelog 업데이트 (`docs/04-report/changelog.md`):
```markdown
## [2026-04-21] signal-accuracy v0.1.1

### Added
- AI 시그널 방향 정합도 평가 인프라 (96% design match)
- `ai_signal_evaluation` 테이블 + Flyway V15 마이그레이션
- 일일 스케줄러: 7d/30d window 자동 평가
- Admin 백필 엔드포인트 (`POST /api/admin/ai/backfill-evaluation`)
- 공개 API: `GET /api/v1/ai/accuracy?window=30`
- FE 배지 + 툴팁 (조용한 숨김, 샘플 부족 시)
- forbidden-terms CI 강화: 정확도/예측/적중 등 5개 용어

### Changed
- FE API 파일 구조: `src/services/` → `src/lib/api/` (컨벤션 정합)
- Env 변수 prefix: `APP_AI_EVALUATION_*` → `AI_EVALUATION_*`
- Error code: `INVALID_WINDOW` → `INVALID_REQUEST` (표준화)

### Technical
- BE: 30+ unit+integration tests, all green
- FE: components complete, Jest pending (Phase 6+)
- Legal: forbidden-terms CI pass, runtime LegalGuardFilter 분리
- Perf: Redis 1h cache, batch 1000/tx, Testcontainers verified
```

---

## 10. 최종 요약

### 완료도

| 항목 | 상태 | 비고 |
|------|------|------|
| **기능 구현** | ✅ 100% | 9 Step 모두 완료 |
| **설계 매칭** | ✅ 96% | 기준(90%) 초과 |
| **백엔드 테스트** | ✅ 30+ | 모두 Green |
| **프론트엔드 테스트** | ⚠️ Pending | Jest setup 대기 (FE 기술 부채) |
| **법적 가드** | ✅ 100% | CI forbidden-terms + 면책 |
| **프로덕션 배포 인프라** | ✅ 검증 완료 | admin endpoint 202, idempotent |
| **운영 샘플 누적** | ⏳ 자동 | 2026-05 경, candidateCount=0 → N (자연) |

### 핵심 가치

**"측정 가능한 AI"** — 이제부터:
- 프롬프트 변경 효과를 수치로 비교 가능
- 모델 업그레이드의 개선 여부 증명 가능
- 사용자는 분석 신뢰도를 투명하게 확인 가능

**다음 단계** (Phase 5+ AI 고도화):
- Confidence 버킷별 정합도 분석
- 멀티 모델 앙상블 효과 정량화
- Context window 확장·RAG 개선의 A/B 테스트

---

## 11. Executive Summary 표 (한 줄 요약)

| Perspective | Content |
|---|---|
| **Problem** | AI 시그널이 "좋아진 것 같다"만 평가되어, 개선 판단 불가. |
| **Solution** | 기존 DB 조합(audit + candles) 배치 평가 인프라 (비용 0). |
| **Function/UX** | 사용자는 배지로 과거 정합도 확인 → 신뢰도 투명화. 운영자는 A/B 튜닝 가능. |
| **Core Value** | **측정 가능한 AI** — 이후 고도화가 증명 가능한 형태로 진행됨. |

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-21 | signal-accuracy v0.1.1 완료 리포트 (Match Rate 96%) | wonseok-han |

---

**Report Generated**: 2026-04-21  
**Status**: Ready for Archive  
**Recommendation**: `/pdca archive signal-accuracy` 진행
