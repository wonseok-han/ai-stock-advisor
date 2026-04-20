---
template: analysis
version: 1.0
feature: notification-news
date: 2026-04-20
author: wonseok-han
project: AI Stock Advisor
status: Approved
---

# notification-news Analysis Report

> **Analysis Type**: Gap Analysis (Design ↔ Implementation)
>
> **Project**: AI Stock Advisor
> **Analyst**: wonseok-han (gap-detector agent)
> **Date**: 2026-04-20
> **Plan Doc**: [notification-news.plan.md](../01-plan/features/notification-news.plan.md)
> **Design Doc**: [notification-news.design.md](../02-design/features/notification-news.design.md)
> **Merge Commit**: `5a87e21` (PR #14, squash merge into `develop`)

---

## 1. Analysis Overview

### 1.1 Purpose

`notification-news` 기능의 PR #14(`5a87e21`) 머지 이후, Plan FR-01~FR-09 + Design FR-10 + NFR-07 요건이 실제 소스에 빠짐없이 반영되었는지 **파일:라인 단위 증거**로 검증하고, Report 단계 진입 가능 여부(Match Rate ≥ 90%)를 판단한다.

### 1.2 Scope

| 항목 | 경로 |
|------|------|
| Plan | `docs/01-plan/features/notification-news.plan.md` |
| Design | `docs/02-design/features/notification-news.design.md` |
| 구현 (BE) | `apps/api/src/main/java/com/aistockadvisor/notification/**` |
| 구현 (DB) | `apps/api/src/main/resources/db/migration/V12__notification_news.sql` |
| 구현 (FE) | `apps/web/public/sw.js` (변경 없음, Design §3.7 의도) |
| 빌드 검증 | `./gradlew check` = **BUILD SUCCESSFUL** |

### 1.3 Method

1. Plan FR × Design FR × 구현 증거 3-way 매핑
2. Design §3 (Component Designs), §5 (Test Plan), §7 (Acceptance Scenarios) 커버리지 검사
3. 불변(§2.4 Invariants)과 회귀 방지(§5.5) 준수 확인
4. Gap 발견 시 priority(high/medium/low) 부여

---

## 2. Functional Requirements — Evidence Mapping

| FR | 요구사항 (요약) | 구현 증거 | 상태 |
|----|----------------|-----------|:---:|
| **FR-01** | `onNewNews=true`+`enabled=true` 에 15분 주기 `checkNewNews(setting)` 호출 | `NotificationCheckService.java:56-91` (`@Scheduled fixedRate=900_000`) + `:83-85` (`if (setting.isOnNewNews()) checkNewNews(setting);`) | ✅ Match |
| **FR-02** | `publishedAt > last_news_published_at` 만 필터링 | `NotificationNewsDedupPolicy.java:45-47` (`n.publishedAt().isAfter(currentWatermark)`) | ✅ Match |
| **FR-03** | 신규 활성화 시 첫 사이클은 baseline 만 세팅, 발송 안 함 | `NotificationNewsDedupPolicy.java:41-43` (`currentWatermark==null → BASELINE`) + `NotificationCheckService.java:154-161` (`case BASELINE → markNewsNotified + save`, push 미호출) | ✅ Match |
| **FR-04** | 신규 2건↑ 시 `{titleKo} 외 N건` body | `NotificationCheckService.java:184-191` (`buildNewsBody`: `newerCount>1 ? headline + " 외 " + (newerCount-1) + "건" : headline`) + 200자 truncate | ✅ Match |
| **FR-05** | Push payload 에 `url` 필드 포함 | `PushService.java:109-119` (`buildPayload` → `{"title","body","icon","url"}` JSON) | ✅ Match |
| **FR-06** | 클릭 시 `/stocks/{ticker}` 로 이동 | `NotificationCheckService.java:167` (`String url = "/stocks/" + target.ticker();`) + `sw.js:29-30` (`const url = event.notification.data?.url \|\| '/'; openWindow(url);`) | ✅ Match |
| **FR-07** | 발송 **성공 시에만** watermark 전진 | `NotificationCheckService.java:169-176` (`if (sent) markNewsNotified + save; else log.debug "watermark not advanced"`) | ✅ Match |
| **FR-08** | `onNewNews=false` 또는 `enabled=false` 면 체크 스킵 | `NotificationCheckService.java:60-62` (`filter(isEnabled)`) + `:83` (`if (setting.isOnNewNews())`) | ✅ Match |
| **FR-09** | 뉴스 fetch 실패 시 사이클 정상 진행 | `NotificationCheckService.java:142-148` (`try { newsService.getNews() } catch { log.debug; return; }`) — 다른 종목/설정 계속 처리됨 | ✅ Match |
| **FR-10** (Design 추가) | 가격 알림도 `url="/stocks/{ticker}"` 포함 | `NotificationCheckService.java:110-116` (`sendToUser(userId, title, body, "/stocks/" + quote.ticker())` 4-arg 호출) | ✅ Match |

**FR Coverage**: **10 / 10 = 100%**

---

## 3. Non-Functional Requirements — Evidence Mapping

| NFR | 요구사항 | 증거 | 상태 |
|-----|---------|------|:---:|
| NFR-01 | 가격 `NotificationDedupPolicy` 불변 | 가격 경로는 `NotificationDedupPolicy.decide(...)` 그대로, 뉴스 경로는 별도 `NotificationNewsDedupPolicy.decide(...)` 독립 호출. 가격 테스트 T1~T9 green 유지. | ✅ |
| NFR-02 | LLM 비용 억제 (24h 번역 캐시 재사용) | `newsService.getNews(ticker, NEWS_FETCH_LIMIT=5)` 만 호출 — 별도 번역 경로 없음 | ✅ |
| NFR-03 | 사이클 당 fetch 호출 ≤ unique ticker 수 | `NotificationCheckService.java:66-77` (`tickers = Set<String>` unique, ticker 당 `getQuote` 1회 + `getNews` 1회/setting). 캐시 히트 시 outbound 트래픽 없음. | ✅ |
| NFR-04 | `./gradlew check` 통과 + 신규 단위 테스트 | BUILD SUCCESSFUL. 신규: `NotificationNewsDedupPolicyTest` (N1~N6), `PushServiceTest` (P1~P5), 확장: `NotificationSettingEntityTest` (U6~U7) | ✅ |
| NFR-05 | Flyway V12 멱등 migration | `V12__notification_news.sql:2-3` (`ADD COLUMN IF NOT EXISTS last_news_published_at TIMESTAMPTZ`) | ✅ |
| NFR-06 | sw.js 하위 호환 (`url` 없으면 기본 동작) | `sw.js:13` (`data: data.url ? { url: data.url } : undefined`) + `:29` (`data?.url \|\| '/'`) | ✅ |
| **NFR-07** | PR 1개 squash merge (`feat/notification-news` → `develop`) | PR #14, commit `5a87e21` squash merged | ✅ |

**NFR Coverage**: **7 / 7 = 100%**

---

## 4. Design §3 Component Coverage

| Design § | Component | 구현 파일 | Match |
|:--------:|-----------|-----------|:-----:|
| §3.1 | Flyway V12 migration | `V12__notification_news.sql` (IF NOT EXISTS + COMMENT) | ✅ 1:1 |
| §3.2 | Entity 필드 + getter + `markNewsNotified` | `NotificationSettingEntity.java` lastNewsPublishedAt 필드, getter, `markNewsNotified(Instant)` | ✅ 1:1 |
| §3.3 | Dedup Policy 순수 함수 + Decision record + Action enum | `NotificationNewsDedupPolicy.java` (Action enum, Decision record, `decide` static) | ✅ 1:1 |
| §3.4 | `NotificationCheckService` NewsService 주입 + `checkNewNews` + 루프 확장 + BASELINE/SEND/NOOP switch | `NotificationCheckService.java` 생성자 주입 + 스케줄러 루프 + 신규 메서드 | ✅ 1:1 |
| §3.5 | `PushService` 3-arg/4-arg 오버로드 + `buildPayload` package-private | `PushService.java` 시그니처·분리 모두 반영 | ✅ 1:1 |
| §3.6 | `NewsService` 재사용 (읽기 전용) | `newsService.getNews(ticker, 5)` 호출만 | ✅ |
| §3.7 | `sw.js` **변경 없음** (이미 url 지원) | git diff 기준 sw.js 미변경. 기존 `data.url` 처리 로직 재사용 | ✅ 의도된 no-op |

**§3 Coverage**: **7 / 7 = 100%**

---

## 5. Design §5 Test Plan Coverage

### 5.1 NotificationNewsDedupPolicyTest (N1~N6)

| # | 시나리오 (Design §5.1) | 구현 테스트 메서드 | 상태 |
|:---:|-----------------------|-----------------|:---:|
| N1 | 뉴스 0건 → NOOP | `n1_emptyListReturnsNoop` | ✅ |
| N2 | watermark=null, 3건 → BASELINE, watermark=최신 | `n2_nullWatermarkYieldsBaseline` | ✅ |
| N3 | watermark=t0, 모두 ≤ t0 → NOOP | `n3_allBelowWatermarkReturnsNoop` | ✅ |
| N4 | watermark=t0, 1건 > t0 → SEND(newerCount=1) | `n4_singleNewerItemReturnsSend` | ✅ |
| N5 | watermark=t0, 3건 중 2건 newer → SEND(target=최신, newerCount=2) | `n5_multipleNewerItemsReturnsSend` | ✅ |
| N6 | publishedAt null 혼재 → null 제외 후 정상 동작 | `n6_nullPublishedAtFilteredOut` (baseline / send / all-null 3케이스) | ✅ 상위 커버 |

### 5.2 NotificationSettingEntityTest (U6~U7)

| # | 시나리오 (Design §5.2) | 구현 테스트 메서드 | 상태 |
|:---:|-----------------------|-----------------|:---:|
| U6 | `markNewsNotified(t0)` → `getLastNewsPublishedAt()==t0` | `u6_markNewsNotifiedSetsWatermark` | ✅ |
| U7 | `update()` 호출해도 `lastNewsPublishedAt` 유지 | `u7_updatePreservesNewsWatermark` (임계값 변경/동일값 재저장 2케이스) | ✅ 상위 커버 |

기존 U1~U5 회귀 green 유지.

### 5.3 PushServiceTest (P1~P5)

| # | 시나리오 (Design §5.3) | 구현 테스트 메서드 | 상태 |
|:---:|-----------------------|-----------------|:---:|
| P1 | 3-arg (url 없음) → url 키 미포함 | `p1_noUrl_omitsUrlKey` | ✅ |
| P2 | `/stocks/AAPL` → url 키 포함 | `p2_withUrl_includesUrlKey` | ✅ |
| P3 | 명시적 null → P1 동일 | `p3_explicitNullUrl_sameAsNoUrl` | ✅ |
| P4 | 빈 문자열 / whitespace → url 키 미포함 | `p4_blankUrl_omitsUrlKey` (`""` + `"   "` 2케이스) | ✅ 상위 커버 |
| — | (Design 예정 외) 추가: 큰따옴표 이스케이프 검증 | `p5_escapesDoubleQuotes` | ✅ Bonus |

> **Note**: P5는 Design §5.3 표에 P1~P4만 명시되었으나 구현에서 JSON 이스케이프 견고성을 위한 추가 테스트로 제공됨. 요건 초과(positive gap) — 감점 아님.

### 5.4 회귀 방지 (Design §5.5)

| 대상 | 상태 |
|------|:---:|
| `NotificationDedupPolicyTest` T1~T9 (가격 경로) | ✅ BUILD SUCCESSFUL 에 포함 — 회귀 없음 |
| `NotificationSettingEntityTest` U1~U5 (`update()` 3-arg 불변) | ✅ 동일 파일에서 green 유지 |

---

## 6. Design §7 Acceptance Scenarios — Traceability

E2E 수동 QA 는 배포 후 Zero Script QA 로 검증 예정(§5.4 Deferred).

| # | 시나리오 | 대응 테스트/코드 경로 | 상태 |
|:---:|---------|----------------------|:---:|
| A1 | 첫 cycle baseline, push 미발송 | N2 (BASELINE 경로) + CheckService L154-161 | ✅ Covered (unit) |
| A2 | 신규 1건 → push 발송 + watermark 전진 | N4 + CheckService L162-177 (SEND 분기) + P2 | ✅ Covered (unit) |
| A3 | 신규 다수 → "외 N건" body | N5 + `buildNewsBody` | ✅ Covered (unit) |
| A4 | 신규 없음 → NOOP | N3 + CheckService L178-180 | ✅ Covered (unit) |
| A5 | Push 실패 시 watermark 불변 → 재시도 가능 | CheckService L169-176 (`if (sent) ... else log.debug`) — FR-07 과 동일 | ✅ Covered (code path) |
| A6 | 알림 클릭 → `/stocks/AAPL` 오픈 | `sw.js:27-31` (notificationclick → openWindow) | ✅ Covered (SW) |
| A7 | `onNewNews=false` 사용자 → 뉴스 체크 스킵 | CheckService L83 (`if (setting.isOnNewNews())`) — FR-08 | ✅ Covered (code path) |
| A8 | 가격 알림 url 확장 (FR-10) | CheckService L110-116 (4-arg) + P2 payload 검증 | ✅ Covered (unit) |

**Deferred(수동)**: A1~A6 E2E 브라우저 실측은 배포 후 Zero Script QA 로 별도 수행. 본 Gap 분석 범위 밖.

---

## 7. §2.4 Invariants 검증

| Invariant | 검증 방법 | 상태 |
|-----------|----------|:---:|
| `NotificationDedupPolicy` (가격) 불변 | 가격 경로는 기존 `NotificationDedupPolicy.decide(...)` 그대로 호출. T1~T9 green. | ✅ |
| `NotificationSettingEntity.update()` 3-arg 시그니처 불변 | `update(BigDecimal, boolean, boolean)` 유지 + U2/U3/U7 회귀 green | ✅ |
| `NewsService.getNews()` 불변 (호출만 추가) | 호출 외 수정 없음 | ✅ |
| `sw.js` 불변 (이미 url 지원) | git diff: 파일 변경 없음 | ✅ |

---

## 8. Clean Architecture / Convention 준수

### 8.1 Layer Placement

| 파일 | 설계 레이어 | 실제 위치 | 상태 |
|------|-----------|----------|:---:|
| `NotificationSettingEntity` | Infrastructure (JPA) | `notification/infra/` | ✅ |
| `NotificationNewsDedupPolicy` | Service (순수 함수, Domain 성격) | `notification/service/` | ✅ |
| `NotificationCheckService` | Service (Application) | `notification/service/` | ✅ |
| `PushService` | Service | `notification/service/` | ✅ |
| `V12__notification_news.sql` | Migration | `db/migration/` | ✅ |

### 8.2 의존성 방향

- `NotificationNewsDedupPolicy` → **의존성 0** (순수 함수, `NewsItem` 만 import). Spring 의존 없음. ✅
- `NotificationCheckService` → `NewsService`, `PushService`, `settingRepo`, `NotificationNewsDedupPolicy` 의존. 상위→하위 방향 준수. ✅
- 가격 경로와 뉴스 경로 상호 의존 없음 (독립). ✅

### 8.3 Naming / Style

| 항목 | 컨벤션 | 구현 | 상태 |
|------|-------|------|:---:|
| 클래스 | PascalCase | `NotificationNewsDedupPolicy`, `NotificationCheckService`, `PushService` | ✅ |
| 메서드 | camelCase | `decide`, `checkNewNews`, `buildNewsBody`, `markNewsNotified`, `buildPayload` | ✅ |
| 상수 | UPPER_SNAKE_CASE | `NEWS_FETCH_LIMIT`, `BODY_MAX_LENGTH` | ✅ |
| 패키지 | lowercase.dot | `com.aistockadvisor.notification.service` | ✅ |
| DTO/Record | record | `NewsItem`, `Decision` | ✅ |

---

## 9. Match Rate Summary

```
┌────────────────────────────────────────────────┐
│  Overall Match Rate: 100%                      │
├────────────────────────────────────────────────┤
│  FR Coverage:               10 / 10  (100%)    │
│  NFR Coverage:               7 /  7  (100%)    │
│  Design §3 Components:       7 /  7  (100%)    │
│  Design §5 Test Plan:       12 / 12  (100%+)   │
│  Design §7 Acceptance:      8 /  8  (unit/code)│
│  §2.4 Invariants:            4 /  4  (100%)    │
│  Architecture/Convention:   PASS               │
├────────────────────────────────────────────────┤
│  Match:          실질 전 항목                   │
│  Missing design:  0                            │
│  Not implemented: 0                            │
│  Positive gap:   1 (PushServiceTest P5 bonus)  │
└────────────────────────────────────────────────┘
```

**Gap 항목 수**: **0 (high/medium/low 전 카테고리에서 누락·불일치 없음)**

---

## 10. Gaps

### 10.1 Missing (Design O, Implementation X)

_없음._

### 10.2 Added (Design X, Implementation O)

| 항목 | 구현 위치 | 영향 | 조치 |
|------|----------|------|------|
| `PushServiceTest.p5_escapesDoubleQuotes` — JSON 이스케이프 견고성 테스트 | `PushServiceTest.java` | Low (positive — 품질 향상) | 설계 §5.3 표에 P5 행 추가 권장 (문서 업데이트, 코드 변경 없음) |

### 10.3 Changed (Design ≠ Implementation)

_없음._

---

## 11. Recommended Actions

### 11.1 Immediate

- **없음** — 매칭률 100%.

### 11.2 Documentation Update (Low priority)

| Priority | 항목 | 위치 |
|:--------:|------|------|
| Low | Design §5.3 표에 P5(JSON 이스케이프 검증) 행 추가 | `docs/02-design/features/notification-news.design.md` §5.3 |

### 11.3 Deferred (별도 feature 후보, 본 기능 범위 밖)

Plan §2 Non-Goals 에 이미 명시:

- 뉴스 개인화/랭킹/sentiment 필터 (별도 feature)
- 사용자별 뉴스 알림 빈도 커스터마이징
- 뉴스 요약 품질 개선 (LLM 재튜닝)
- 푸시 실패 시 email fallback

---

## 12. Next Steps

- [x] Match Rate ≥ 90% 확인 → **100%**
- [ ] Report 단계 진입: `/pdca report notification-news`
- [ ] (선택) Design §5.3 문서에 P5 행 보강
- [ ] Report 승인 후 `/pdca archive notification-news` 로 `docs/archive/2026-04/notification-news/` 이동

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2026-04-20 | 초기 Gap 분석 — PR #14 (`5a87e21`) 기준. FR-01~FR-10 + NFR-01~NFR-07 모두 구현 확인, 테스트 N1~N6/U6~U7/P1~P5 green. Match Rate 100%. | wonseok-han |
