---
template: report
version: 1.0
feature: notification-news
date: 2026-04-20
author: wonseok-han
project: AI Stock Advisor
phase: completed
matchRate: 100
status: Done
---

# notification-news Completion Report

> **Phase**: PDCA 완료 (Plan → Design → Do → Check → Act 불필요)
>
> **Project**: AI Stock Advisor (Phase 4.5.3)
> **Feature**: 뉴스 알림 (notification-news)
> **Duration**: 2026-04-20 (Plan · Design · Do · Check · Report 동일 세션)
> **Merge**: PR [#14](https://github.com/wonseok-han/ai-stock-advisor/pull/14) → `develop` `5a87e21` (squash merge)

---

## Executive Summary

### 1.1 Project Overview

| 항목 | 값 |
|------|----|
| Feature | `notification-news` |
| Scope | 뉴스 알림 (15분 스케줄, watermark dedup, FR-10 url 일관성) |
| PDCA 시작 | 2026-04-20 (Plan) |
| PDCA 완료 | 2026-04-20 (Report) |
| Duration | 약 1 세션 (Plan → Design → Do → Check → Report 일괄 진행) |
| Project Level | Dynamic |

### 1.2 Results Summary

| 항목 | 값 |
|------|----|
| **Match Rate** | **100%** (Gap 0) |
| 구현 파일 | 8개 (BE 5 + 테스트 3) + DB 1 |
| FE 변경 파일 | **0개** (`sw.js` 이미 `data.url` 지원) |
| 코드 라인 | +1018 / -12 (문서 포함) |
| 신규 단위 테스트 | **13개** (N1~N6 + U6~U7 + P1~P5) |
| 회귀 테스트 | T1~T9, U1~U5 전부 green 유지 |
| 빌드 | `./gradlew check` **BUILD SUCCESSFUL** |
| PR | #14 squash merge, branch 자동 삭제 |
| Iteration | 0회 (100% 달성으로 Act 불필요) |

### 1.3 Value Delivered (4 perspectives)

| 관점 | 기획 목표 | 실제 달성 | 측정 지표 |
|------|----------|-----------|----------|
| **Problem** | `onNewNews` 토글이 UI/DTO/Entity/DB 에 존재하나 발송 로직 부재 — 죽은 토글. 사용자가 관심 종목 뉴스를 놓치지 않으려면 매번 앱을 열어야 함. | ✅ 해결 — 15분 스케줄러가 `onNewNews=true` 설정을 순회하여 신규 뉴스를 Web Push 로 발송. | `NotificationCheckService.check()` 루프에 `checkNewNews()` 배선 완료. FR-08 분기로 `onNewNews=false` 는 즉시 스킵. |
| **Solution** | 15분 스케줄러에 `checkNewNews()` 추가, 기존 `NewsService.getNews()` 24h 번역 캐시 재사용으로 LLM 비용 0~최소. Watermark(`last_news_published_at`) 로 중복 차단, 첫 활성화 baseline. | ✅ 전부 구현 — Flyway V12 컬럼 추가, `NotificationNewsDedupPolicy` 순수 함수 (BASELINE/SEND/NOOP 3-way), `PushService` 4-arg 오버로드로 `url` payload 추가. | 신규 파일 3개 (Policy, V12, PushServiceTest) + 수정 4개. 테스트 N1~N6, U6~U7, P1~P5 전부 green. Invariants 4개(가격 Policy·Entity 3-arg·NewsService·sw.js) 준수. |
| **Function UX Effect** | 관심 종목 알림 설정자는 15분 이내 신규 뉴스를 한국어 제목으로 푸시 수신, 앱 미오픈 상태에서도 핵심 이벤트 인지. 알림 탭 시 종목 상세(`/stocks/{ticker}`) 로 이동. | ✅ 설계대로 구현 — `{ticker} 새 뉴스` 제목 + `{titleKo} 외 N건` body (200자 truncate) + `url` payload. 가격 알림도 FR-10 으로 동일한 url 적용 (UX 일관성). | CheckService buildNewsBody 라인: `newerCount>1 ? headline + " 외 " + (newerCount-1) + "건" : headline`. sw.js 기존 `notificationclick → openWindow(data.url)` 재사용. |
| **Core Value** | 기존 파이프라인 100% 재사용 + Entity/스케줄러 불변 유지. V12는 `ADD COLUMN IF NOT EXISTS` 1줄로 idempotent. BE 5파일 추가, FE 0파일. "저비용·고가치 알림" 포지셔닝 완성. | ✅ 초과 달성 — FE 변경 0건 유지, `NewsService`·`NotificationDedupPolicy`·`NotificationSettingEntity.update()` 3-arg 시그니처 모두 불변. 추가로 FR-10(디자인 단계 발견) 으로 가격 알림에도 url 적용. | diff: +1018/-12. Rollback 시 컬럼만 남음(데이터 손실 X). sw.js git diff 0 lines. LLM 호출 증가량 = 0 (24h 번역 캐시가 뉴스 화면/알림 양쪽에서 공유됨). |

---

## 2. PDCA Summary

```
[Plan] ✅ → [Design] ✅ → [Do] ✅ → [Check] ✅ (100%) → [Act] ⏭ (불필요) → [Report] ✅
```

### 2.1 Plan Phase

- **산출물**: `docs/01-plan/features/notification-news.plan.md` (143 lines)
- **핵심**: FR-01~FR-09 정의, NFR-07 (PR 1개 squash merge), 10-step Implementation Order
- **결정**: YAGNI 원칙으로 `onSignalChange` 재도입 금지, `onNewNews` 부활만 집중

### 2.2 Design Phase

- **산출물**: `docs/02-design/features/notification-news.design.md` (503 lines, 10개 섹션)
- **핵심 설계**:
  - Watermark 패턴 (`last_news_published_at` TIMESTAMPTZ) — hysteresis 가 아닌 이산 이벤트용
  - 순수 함수 `NotificationNewsDedupPolicy.decide(news, watermark)` → `Decision(action, target, newerCount, watermark)`
  - PushService 4-arg 오버로드 + `buildPayload` package-private 분리
- **Design 단계 발견**: FR-10 (가격 알림에도 `/stocks/{ticker}` url 포함) — UX 일관성
- **Pre-design finding**: `sw.js` 이미 `data.url` 지원 → FE 변경 0건 결정

### 2.3 Do Phase

구현 순서 (Design §10 충실 이행):

| Step | 대상 | 결과 |
|:----:|------|------|
| 1 | `V12__notification_news.sql` | `ADD COLUMN IF NOT EXISTS` + `COMMENT` |
| 2 | Entity `lastNewsPublishedAt` + getter + `markNewsNotified(Instant)` | U6/U7 테스트 동반 |
| 3 | `NotificationNewsDedupPolicy` 순수 함수 | N1~N6 테스트 신규 (120 lines) |
| 4 | `PushService` 4-arg 오버로드 + `buildPayload` package-private | P1~P5 테스트 신규 (59 lines) |
| 5 | `NotificationCheckService` NewsService 주입 + `checkNewNews()` + 루프 연결 | 기존 `@Scheduled` 재사용 |
| 6 | FR-10: 가격 알림 `/stocks/{ticker}` url 추가 | `checkPriceThreshold` SEND 분기에 4-arg 호출 |
| 7 | `./gradlew check` | BUILD SUCCESSFUL 38s |
| 8 | commit `0da2f4b` + push + PR #14 + squash merge + 브랜치 삭제 | 자동 `fast-forward develop` |

### 2.4 Check Phase

- **산출물**: `docs/03-analysis/notification-news.analysis.md` (12개 섹션)
- **결과**: **Match Rate 100% | Gap 0**
- **검증 커버리지**:
  - FR 10/10 · NFR 7/7 · Design §3 컴포넌트 7/7 · §5 테스트 12/12 · §7 수용 시나리오 8/8 · Invariants 4/4
- **Positive gap**: `PushServiceTest.p5_escapesDoubleQuotes` — Design 표에 없던 JSON 이스케이프 견고성 테스트 추가 (품질 초과분)

### 2.5 Act Phase

**불필요** — Match Rate 100% 달성으로 `/pdca iterate` 생략.

---

## 3. Implementation Metrics

### 3.1 File Changes

| 파일 | 종류 | 라인 변화 |
|------|:----:|---------:|
| `apps/api/src/main/resources/db/migration/V12__notification_news.sql` | 신규 | +6 |
| `apps/api/src/main/java/com/aistockadvisor/notification/infra/NotificationSettingEntity.java` | 수정 | +13 |
| `apps/api/src/main/java/com/aistockadvisor/notification/service/NotificationNewsDedupPolicy.java` | 신규 | +53 |
| `apps/api/src/main/java/com/aistockadvisor/notification/service/NotificationCheckService.java` | 수정 | +76 / -12 |
| `apps/api/src/main/java/com/aistockadvisor/notification/service/PushService.java` | 수정 | +26 / -0 |
| `apps/api/src/test/java/com/aistockadvisor/notification/service/NotificationNewsDedupPolicyTest.java` | 신규 | +120 |
| `apps/api/src/test/java/com/aistockadvisor/notification/infra/NotificationSettingEntityTest.java` | 수정 | +31 |
| `apps/api/src/test/java/com/aistockadvisor/notification/service/PushServiceTest.java` | 신규 | +59 |
| `apps/web/public/sw.js` | **변경 없음** | 0 |
| `docs/01-plan/features/notification-news.plan.md` | 신규 | +143 |
| `docs/02-design/features/notification-news.design.md` | 신규 | +503 |
| **합계** | — | **+1018 / -12** |

### 3.2 Test Coverage

| 범주 | 신규 | 회귀 |
|------|:---:|:----:|
| 정책 (Policy) — Pure function | **N1~N6** (6건, NotificationNewsDedupPolicyTest) | T1~T9 (가격 Policy) green |
| 엔티티 (Entity) | **U6, U7** (2건) | U1~U5 green |
| 푸시 (PushService) | **P1, P2, P3, P4, P5** (5건, P5 bonus) | — |
| 빌드 | `./gradlew check` = BUILD SUCCESSFUL 38s | — |

### 3.3 Invariants 준수

| Invariant | 준수 증거 |
|-----------|----------|
| 가격 `NotificationDedupPolicy` 불변 | 호출만 추가, 정책 로직 미변경 |
| `NotificationSettingEntity.update()` 3-arg 시그니처 | `update(BigDecimal, boolean, boolean)` 유지 |
| `NewsService.getNews()` 불변 | 호출만 추가, 시그니처·구현 미변경 |
| `sw.js` 불변 | git diff: 0 lines 변경 |

---

## 4. Key Decisions

### 4.1 Watermark vs Hysteresis

- **결정**: 뉴스는 이산 이벤트이므로 히스테리시스 부적합 → **watermark (`last_news_published_at`)** 채택
- **근거**: 가격은 연속 상태(%변동)이므로 hysteresis/cooldown 이 자연스럽지만, 뉴스는 "특정 시각 이후 신규 항목" 필터만 필요 → 단순 비교 1회로 충분

### 4.2 순수 함수 분리 (NotificationDedupPolicy 선례 적용)

- **결정**: `NotificationNewsDedupPolicy` 도 Spring 의존 없는 `final class` + `static decide(...)` 로 구현
- **효과**: 단위 테스트 N1~N6 이 Mock 없이 순수 입력→출력 검증 가능, 가격 경로와 독립

### 4.3 PushService 오버로드 방식

- **결정**: 기존 3-arg `sendToUser(userId, title, body)` 는 4-arg 에 `null` 위임하는 wrapper 로 보존
- **효과**: 가격 알림 기존 호출자 영향 0, FR-10 은 명시적으로 4-arg 호출로 업그레이드

### 4.4 FR-10 추가 (Design 단계 발견)

- **결정**: Plan 에는 없던 "가격 알림도 url 포함" 을 Design §4 로 추가
- **근거**: 뉴스 알림만 `/stocks/{ticker}` 이동이면 UX 일관성 결여. 마침 오버로드 도입 기회였음
- **효과**: `PushService.buildPayload` 한 곳에서만 url 처리, 가격/뉴스 공통 UX

### 4.5 sw.js FE 변경 0건

- **발견**: Pre-design 점검 시 `sw.js` 가 이미 `data.url` 을 저장·사용하고 있음
- **효과**: FE 파일 수정 없음 → Vercel 배포 리스크 축소, 범위 BE 로 한정

---

## 5. Lessons Learned

### 5.1 What Went Well

- **Pre-design 인프라 점검**: `sw.js`, `NewsService`, `PushService` 현황을 설계 직전에 확인 → FE 0건·LLM 0건 결정 조기 확정
- **순수 함수 + 오버로드 패턴**: 기존 `NotificationDedupPolicy` 선례 재사용, 유사 구조로 학습 비용 최소화
- **Design 단계 FR 추가**: FR-10 을 설계 단계에서 포착해 동일 PR 에 포함 → UX 일관성 확보
- **단일 세션 완주**: Plan → Design → Do → Check → Report 전 과정을 하루에 마감 (Dynamic level 전형 플로우)

### 5.2 Could Improve

- **Design §5.3 문서 반영 누락**: 테스트 구현 시 추가한 P5 (JSON 이스케이프) 가 Design 표에 미반영. 향후 Do 단계에서 테스트 추가 시 Design 동시 업데이트 루틴 필요 (Low priority — 기능 영향 없음)
- **Zero Script QA 연기**: A1~A6 수동 검증은 배포 후 실행 예정. 현 시점까지는 단위/통합 테스트로만 커버 (설계상 deferred 이며 감점 아님)

### 5.3 Reusable Patterns

| 패턴 | 적용처 | 설명 |
|------|-------|------|
| Watermark dedup | 이산 이벤트 알림 | `last_xxx_at` 컬럼 + `publishedAt > watermark` 필터 + BASELINE 첫 사이클 |
| 4-arg overload with wrapper | 레거시 호환 진화 | 기존 시그니처를 새 시그니처의 wrapper 로 재구현, 호출자 변경 0 |
| FE 0 변경 설계 | 기존 SW/프로토콜 활용 | 구현 전 FE 수신부 현황 확인 → payload 키 추가만으로 기능 완성 |
| Pure function + record Decision | 단위 테스트 친화 | Spring 의존 없는 `final class`, `static decide`, immutable `Decision` |

---

## 6. Follow-up Items

### 6.1 Immediate (본 PDCA 범위 내 잔여)

- [x] `/pdca report notification-news` → 본 문서 생성
- [ ] `/pdca archive notification-news` → `docs/archive/2026-04/notification-news/` 로 이동 + _INDEX 업데이트

### 6.2 Post-deploy (별도 작업)

- [ ] **Zero Script QA**: 실제 ticker(e.g. AAPL) 로 A1(baseline) → A2(신규 뉴스) → A6(클릭 이동) 수동 검증 (배포 후)
- [ ] Design §5.3 문서에 P5 행 추가 (Low priority, 기능 영향 없음)

### 6.3 Future Features (Plan §2 Non-Goals — 본 PR 범위 밖)

- 뉴스 개인화/랭킹/sentiment 필터
- 사용자별 뉴스 알림 빈도 커스터마이징 (현재는 시스템 전역 15분 고정)
- 뉴스 요약 품질 개선 (LLM 재튜닝)
- 푸시 발송 실패 시 email fallback
- `notification-signal` (AI 시그널 변화 알림 — `onSignalChange` 재도입 시)

---

## 7. References

- **Plan**: [notification-news.plan.md](../../01-plan/features/notification-news.plan.md)
- **Design**: [notification-news.design.md](../../02-design/features/notification-news.design.md)
- **Analysis**: [notification-news.analysis.md](../../03-analysis/notification-news.analysis.md)
- **PR**: [#14](https://github.com/wonseok-han/ai-stock-advisor/pull/14)
- **Merge Commit**: `5a87e21`
- **Related (선행)**:
  - [notification-dedup](../../archive/2026-04/notification-dedup/) — 가격 알림 hysteresis + cooldown, 순수 함수 Policy 선례
  - [notification-ui-cleanup](../../archive/2026-04/notification-ui-cleanup/) — `onSignalChange` 삭제, `onNewNews` 보존 결정

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2026-04-20 | 초기 완료 리포트 — Match Rate 100%, 13 신규 테스트, FE 0건, +1018/-12 lines. | wonseok-han |
