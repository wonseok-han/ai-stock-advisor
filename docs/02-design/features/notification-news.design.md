---
template: design
version: 1.0
feature: notification-news
date: 2026-04-20
author: wonseok-han
project: AI Stock Advisor
status: Draft
---

# notification-news Design

> **Plan**: [notification-news.plan.md](../../01-plan/features/notification-news.plan.md)

---

## 1. Scope & Reference

### 1.1 목표 재확인 (Plan 기준)
- G1: `onNewNews=true` 설정에 대해 15분 주기 신규 뉴스 Web Push 발송
- G2: Watermark(`last_news_published_at`) 기반 중복 차단 + 첫 활성화 baseline
- G3: 알림 탭 시 `/stocks/{ticker}` 로 이동
- G4: 기존 `NewsService` 파이프라인 재사용, 추가 LLM 비용 최소화

### 1.2 참조 문서
- Phase 2 RAG: `docs/archive/2026-04/phase2-rag-pipeline/` (NewsService, NewsTranslator)
- Phase 4 Auth: `docs/archive/2026-04/auth/` (Web Push, VAPID, sw.js)
- Phase 4.5.1 Dedup: `docs/archive/2026-04/notification-dedup/` (NotificationDedupPolicy 순수 함수 패턴)

### 1.3 기존 인프라 확인 결과 (Pre-design findings)
- **`sw.js` 는 이미 `data.url` 을 처리** (`push` 이벤트에 `options.data = { url }`, `notificationclick` 에 `clients.openWindow(url)`). **→ FE 변경 불필요**
- `NewsService.getNews(ticker, limit)` 는 24h 번역 캐시 hit 시 LLM 호출 없음
- `PushService.sendToUser()` 는 이미 `boolean` 반환 (fail-safe)
- `NotificationSettingEntity` 는 `onNewNews` 필드/getter 존재 (죽은 상태)
- 기존 마이그레이션 V1~V11 사용 중 → V12 할당

---

## 2. Architecture Overview

### 2.1 기존 흐름 (가격 알림)
```
@Scheduled(15min)
NotificationCheckService.check()
  └─> per enabled setting
       └─> checkPriceThreshold(setting, quote, now)
            └─> NotificationDedupPolicy.decide(...)
                 └─> SEND/SKIP/RESET/NOOP
```

### 2.2 신규 추가 흐름 (뉴스 알림)
```
@Scheduled(15min)   ← 기존 스케줄러 재사용
NotificationCheckService.check()
  └─> per enabled setting (onNewNews=true)
       └─> checkNewNews(setting, now)
            ├─> NewsService.getNews(ticker, limit=5)            ← 24h 캐시 우선
            └─> NotificationNewsDedupPolicy.decide(news, setting.lastNewsPublishedAt)
                 ├─> BASELINE   : 첫 사이클 — watermark 만 세팅
                 ├─> SEND       : 신규 뉴스 존재 (publishedAt > watermark)
                 └─> NOOP       : 신규 뉴스 없음
```

### 2.3 상태 모델
- 추가 필드: `notification_settings.last_news_published_at` (`Instant`, nullable)
- `NULL` → 첫 활성화 상태. 다음 사이클에서 baseline 설정 + send skip.
- Non-null → 이 시각 이후 published 된 뉴스만 발송 후보.

### 2.4 불변 (Invariants)
- `NotificationDedupPolicy` (가격) 불변 — 뉴스 경로 독립
- `NotificationSettingEntity.update(threshold, onNewNews, enabled)` 3-arg 시그니처 불변
- `NewsService.getNews()` 불변 — 호출만 추가
- `sw.js` 불변 — 이미 `data.url` 지원

---

## 3. Component Designs

### 3.1 DB Schema (Flyway V12)

**파일**: `apps/api/src/main/resources/db/migration/V12__notification_news.sql`

```sql
-- Phase 4.5.3: notification-news feature — 뉴스 알림 watermark
ALTER TABLE notification_settings
    ADD COLUMN IF NOT EXISTS last_news_published_at TIMESTAMPTZ;

COMMENT ON COLUMN notification_settings.last_news_published_at IS
  '마지막으로 알림 발송한 뉴스의 published_at. NULL 이면 baseline 필요 (첫 사이클).';
```

- `IF NOT EXISTS` 로 멱등성 확보
- 기본값 없음 (NULL) — Entity 기본값으로 노출됨
- Index 불필요 — 읽기 시 `setting.lastNewsPublishedAt` 은 같은 row 의 컬럼

### 3.2 Entity 변경

**파일**: `apps/api/src/main/java/.../notification/infra/NotificationSettingEntity.java`

**추가 필드**:
```java
@Column(name = "last_news_published_at")
private Instant lastNewsPublishedAt;  // nullable (baseline 필요 표지)
```

**추가 getter**:
```java
public Instant getLastNewsPublishedAt() { return lastNewsPublishedAt; }
```

**추가 메서드**:
```java
/**
 * 뉴스 알림 watermark 전진. 푸시 발송 성공 or baseline 세팅 모두 호출.
 * @param publishedAt 최신 뉴스의 published_at
 */
public void markNewsNotified(Instant publishedAt) {
    this.lastNewsPublishedAt = publishedAt;
}
```

**중요**: `update()` 시그니처는 그대로 3-arg 유지. 뉴스 watermark 는 시스템이 관리하므로 사용자 입력으로 변경되지 않음.

### 3.3 Dedup Policy (순수 함수)

**파일**: `apps/api/src/main/java/.../notification/service/NotificationNewsDedupPolicy.java` (신규)

```java
package com.aistockadvisor.notification.service;

import com.aistockadvisor.news.domain.NewsItem;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 뉴스 알림 중복 억제 정책. 순수 함수 — DI 없음.
 *
 * 규칙:
 *   1. BASELINE: watermark 가 null 이면 최신 뉴스 publishedAt 으로 세팅만 (발송 X).
 *   2. SEND: watermark 보다 나중에 published 된 뉴스가 1건 이상이면 발송.
 *   3. NOOP: 뉴스 없음 또는 모두 watermark 이하.
 *
 * SEND Decision 에는 "발송 대상 뉴스" 와 "전진할 새 watermark" 포함.
 */
public final class NotificationNewsDedupPolicy {

    private NotificationNewsDedupPolicy() {}

    public enum Action { SEND, BASELINE, NOOP }

    /**
     * @param target     발송 대상 최신 1건 (SEND 일 때만 non-null)
     * @param newerCount watermark 이후 뉴스 개수 (SEND body 포맷용)
     * @param watermark  전진할 새 watermark (BASELINE or SEND 일 때 non-null)
     */
    public record Decision(Action action, NewsItem target, int newerCount, Instant watermark) {}

    public static Decision decide(List<NewsItem> news, Instant currentWatermark) {
        if (news == null || news.isEmpty()) {
            return new Decision(Action.NOOP, null, 0, null);
        }
        // 최신순 정렬 (NewsItem.publishedAt DESC)
        List<NewsItem> sorted = news.stream()
                .filter(n -> n.publishedAt() != null)
                .sorted(Comparator.comparing(NewsItem::publishedAt).reversed())
                .toList();
        if (sorted.isEmpty()) {
            return new Decision(Action.NOOP, null, 0, null);
        }
        Instant latest = sorted.get(0).publishedAt();

        if (currentWatermark == null) {
            return new Decision(Action.BASELINE, null, 0, latest);
        }

        List<NewsItem> newer = sorted.stream()
                .filter(n -> n.publishedAt().isAfter(currentWatermark))
                .toList();
        if (newer.isEmpty()) {
            return new Decision(Action.NOOP, null, 0, null);
        }
        return new Decision(Action.SEND, newer.get(0), newer.size(), latest);
    }
}
```

### 3.4 NotificationCheckService 확장

**파일**: `apps/api/src/main/java/.../notification/service/NotificationCheckService.java`

**변경**:
- 생성자에 `NewsService newsService` 추가
- `check()` 루프에서 `checkNewNews(setting, now)` 추가 호출
- 신규 private method `checkNewNews(setting, now)`

**`check()` 수정 (일부)**:
```java
for (NotificationSettingEntity setting : settings) {
    checkPriceThreshold(setting, quote, now);       // 기존
    if (setting.isOnNewNews()) {                    // 신규
        checkNewNews(setting, now);
    }
}
```

**신규 메서드**:
```java
private static final int NEWS_FETCH_LIMIT = 5;
private static final int BODY_MAX_LENGTH = 200;

private void checkNewNews(NotificationSettingEntity setting, OffsetDateTime now) {
    List<NewsItem> news;
    try {
        news = newsService.getNews(setting.getTicker(), NEWS_FETCH_LIMIT);
    } catch (Exception e) {
        log.debug("News fetch skipped for {}: {}", setting.getTicker(), e.getMessage());
        return;
    }

    NotificationNewsDedupPolicy.Decision decision =
            NotificationNewsDedupPolicy.decide(news, setting.getLastNewsPublishedAt());

    switch (decision.action()) {
        case BASELINE -> {
            setting.markNewsNotified(decision.watermark());
            settingRepo.save(setting);
            log.debug("News baseline set for {} {}: {}",
                    setting.getUserId(), setting.getTicker(), decision.watermark());
        }
        case SEND -> {
            NewsItem target = decision.target();
            String title = target.ticker() + " 새 뉴스";
            String headline = target.titleKo() != null ? target.titleKo() : target.titleEn();
            String body = buildNewsBody(headline, decision.newerCount());
            String url = "/stocks/" + target.ticker();

            boolean sent = pushService.sendToUser(setting.getUserId(), title, body, url);
            if (sent) {
                setting.markNewsNotified(decision.watermark());
                settingRepo.save(setting);
            } else {
                log.debug("News push returned false for {} {}; watermark not advanced",
                        setting.getUserId(), setting.getTicker());
            }
        }
        case NOOP -> { /* no-op */ }
    }
}

private static String buildNewsBody(String headline, int newerCount) {
    String base = newerCount > 1
            ? headline + " 외 " + (newerCount - 1) + "건"
            : headline;
    return base.length() > BODY_MAX_LENGTH
            ? base.substring(0, BODY_MAX_LENGTH - 1) + "…"
            : base;
}
```

### 3.5 PushService payload 확장 (`url` 파라미터 추가)

**파일**: `apps/api/src/main/java/.../notification/service/PushService.java`

**변경**: `sendToUser` 오버로드 또는 optional 파라미터 추가. **오버로드 방식** 선택 (기존 호출부 변경 최소화 + 명시적).

```java
/** 기존 시그니처 유지 — url 없음. */
public boolean sendToUser(UUID userId, String title, String body) {
    return sendToUser(userId, title, body, null);
}

/**
 * @param url 클릭 시 이동할 경로 (예: "/stocks/AAPL"). null 이면 sw 에서 "/" 기본.
 */
public boolean sendToUser(UUID userId, String title, String body, String url) {
    if (webPushService == null) {
        log.debug("Push disabled — skipping notification for user {}", userId);
        return false;
    }
    List<PushSubscriptionEntity> subs = subscriptionRepo.findByUserId(userId);
    if (subs.isEmpty()) return false;

    String payload = buildPayload(title, body, url);
    int success = 0;
    for (PushSubscriptionEntity sub : subs) {
        try {
            Notification notification = new Notification(
                    sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
            webPushService.send(notification);
            success++;
        } catch (Exception e) {
            log.warn("Push send failed for endpoint {}: {}", sub.getEndpoint(), e.getMessage());
        }
    }
    return success > 0;
}

private static String buildPayload(String title, String body, String url) {
    String escapedTitle = title.replace("\"", "\\\"");
    String escapedBody = body.replace("\"", "\\\"");
    if (url == null || url.isBlank()) {
        return "{\"title\":\"%s\",\"body\":\"%s\",\"icon\":\"/icon.svg\"}"
                .formatted(escapedTitle, escapedBody);
    }
    String escapedUrl = url.replace("\"", "\\\"");
    return "{\"title\":\"%s\",\"body\":\"%s\",\"icon\":\"/icon.svg\",\"url\":\"%s\"}"
            .formatted(escapedTitle, escapedBody, escapedUrl);
}
```

**기존 호출부 영향**:
- `NotificationCheckService.checkPriceThreshold()` — 3-arg 호출 유지 → 내부적으로 null url 전달 → 동일 JSON 생성
- **가격 알림도 이 기회에 `url = "/stocks/{ticker}"` 추가** 권장 (ticker 맥락상 자연스러움)

**결정**: 가격 알림도 `/stocks/{ticker}` URL 포함으로 변경 (일관성). 별도 FR 로 추가.

### 3.6 NewsService 재사용 (읽기 전용)

- `newsService.getNews(ticker, 5)` 호출만 추가
- 반환 `List<NewsItem>` — `publishedAt`, `titleKo`, `titleEn`, `sourceUrl`, `ticker` 필드 사용
- **변경 없음** — 기존 Phase 2 구현 그대로

### 3.7 Service Worker (변경 없음)

`apps/web/public/sw.js` 는 이미 `data.url` 처리 완비:
```js
// push event — payload.url 을 notification.data.url 로 복사
data: data.url ? { url: data.url } : undefined

// notificationclick — data.url 있으면 openWindow, 없으면 "/"
const url = event.notification.data?.url || '/';
event.waitUntil(clients.openWindow(url));
```

**결론**: FE 변경 0건. BE PushService 가 payload 에 `url` 을 포함시키기만 하면 됨.

---

## 4. Additional FR (Design 중 추가 식별)

| FR | 요구사항 | 수용 기준 |
|----|---------|-----------|
| FR-10 | **가격 알림도 url 포함** (일관성) | `checkPriceThreshold()` SEND 시 `url = "/stocks/" + ticker` 로 4-arg 호출 |

(Plan 의 FR-01~FR-09 와 병기)

---

## 5. Test Plan

### 5.1 Unit — NotificationNewsDedupPolicyTest (신규)

| # | 시나리오 | 기대 Action | 기대 watermark |
|---|---------|:----------:|---|
| N1 | 뉴스 0건 | NOOP | null |
| N2 | watermark=null, 뉴스 3건 | BASELINE | 최신 뉴스 publishedAt |
| N3 | watermark=t0, 뉴스 모두 publishedAt ≤ t0 | NOOP | null |
| N4 | watermark=t0, 뉴스 1건 publishedAt > t0 | SEND (newerCount=1) | 해당 뉴스 publishedAt |
| N5 | watermark=t0, 뉴스 3건 중 2건 publishedAt > t0 | SEND (newerCount=2, target=최신) | 최신 publishedAt |
| N6 | 뉴스 중 publishedAt null 혼재 | null 제외 후 정상 동작 | 최신 non-null publishedAt |

### 5.2 Unit — NotificationSettingEntityTest 확장

| # | 시나리오 | 기대 |
|---|---------|------|
| U6 | `markNewsNotified(t0)` 호출 후 `getLastNewsPublishedAt()` == t0 | ✅ |
| U7 | `update()` 호출해도 `lastNewsPublishedAt` 은 유지 (가격 상태와 독립) | ✅ — `update()` 는 `lastNewsPublishedAt` 건드리지 않음 |

### 5.3 Unit — PushService payload 검증

| # | 시나리오 | 기대 payload 키 |
|---|---------|---|
| P1 | `sendToUser(userId, title, body)` 기존 3-arg | `title`, `body`, `icon` (url 부재) |
| P2 | `sendToUser(userId, title, body, "/stocks/AAPL")` | `title`, `body`, `icon`, `url` 포함 |
| P3 | `sendToUser(userId, title, body, null)` | P1 과 동일 (url 없음) |
| P4 | `sendToUser(userId, title, body, "")` | P1 과 동일 (빈 문자열은 skip) |

**주의**: PushService 는 `webPushService` 초기화 안 된 상태면 조기 return — 이 테스트들은 payload 빌드 함수(`buildPayload`)를 package-private 하거나 reflection 으로 검증. **결정**: `buildPayload` 를 package-private static 으로 노출 → 직접 단위 테스트.

### 5.4 통합 시나리오 (Zero Script QA, Deferred)

- 실제 ticker(e.g. AAPL) 에 알림 설정 후 수동으로 `last_news_published_at=NULL` 상태에서 스케줄러 1 cycle 실행
- 첫 cycle: 로그에 `News baseline set` 확인, push 미발송
- 2nd cycle: 신규 뉴스 없으면 NOOP
- Finnhub 에 신규 뉴스 도착 시뮬레이션 → 3rd cycle 에서 push 발송 확인

### 5.5 회귀 방지

| 기존 테스트 | 기대 |
|------------|------|
| `NotificationDedupPolicyTest` T1~T9 | 영향 없음 (가격 경로 독립) — 모두 green 유지 |
| `NotificationSettingEntityTest` U1~U5 | `update()` 3-arg 시그니처 불변 — 모두 green 유지 |

---

## 6. Migration & Rollback Strategy

### 6.1 배포 순서
1. V12 migration 적용 → `last_news_published_at` 컬럼 추가 (모든 기존 row NULL)
2. 새 BE 배포 → `checkNewNews()` 활성화
3. 첫 스케줄 cycle: 모든 `onNewNews=true` 설정이 baseline 상태 → push 없이 watermark 세팅
4. 2번째 cycle 부터 정상 동작

### 6.2 Rollback
- V12 는 컬럼 ADD 만 — 이전 코드로 rollback 해도 컬럼은 무시됨 (이전 Entity 에는 필드 부재)
- 필요 시 수동 `DROP COLUMN last_news_published_at` (데이터 손실 허용 — watermark 는 재설정 가능)
- Flyway migration 되돌림은 이력 보존 위해 별도 V13 로 drop 추가 (비상시)

### 6.3 Feature Flag
- 별도 플래그 불필요 — `onNewNews=true` 인 사용자만 영향
- 필요 시 `app.notification.news.enabled: false` 로 글로벌 kill switch 추가 가능 (현재는 YAGNI)

---

## 7. Acceptance Scenarios (E2E 관점)

### A1. 신규 활성화 사용자의 첫 cycle (Baseline)
- Given: 사용자가 AAPL 알림 설정 (`onNewNews=true`, `lastNewsPublishedAt=NULL`)
- When: 15분 스케줄 실행
- Then: Push 미발송, `lastNewsPublishedAt` 에 현재 최신 뉴스 publishedAt 저장

### A2. 신규 뉴스 발생 (기본 케이스)
- Given: A1 이후 상태, Finnhub 에 최신 뉴스 1건 신규
- When: 다음 15분 cycle
- Then: Push 1회 수신 — `title="AAPL 새 뉴스"`, `body=한국어 제목`, `url="/stocks/AAPL"`, watermark 전진

### A3. 신규 뉴스 다수 발생
- Given: A1 이후, 3건 신규 뉴스 (최신순 N1, N2, N3)
- When: 다음 cycle
- Then: Push 1회 — `body="{N1.titleKo} 외 2건"`, watermark = N1.publishedAt

### A4. 신규 없음
- Given: A1 이후, 뉴스 변화 없음
- When: 다음 cycle
- Then: Push 미발송, watermark 불변

### A5. Push 실패 시 재시도
- Given: A2 조건, `PushService.sendToUser()` 모든 endpoint 실패 → false 반환
- When: 다음 cycle
- Then: watermark 불변 → 동일 뉴스 재발송 시도 (fail-safe)

### A6. 알림 클릭
- Given: A2 에서 받은 알림을 브라우저에서 클릭
- When: notificationclick 이벤트
- Then: `/stocks/AAPL` 탭 열림 (sw.js 기존 로직)

### A7. `onNewNews=false` 사용자
- Given: AAPL 설정, `onNewNews=false`, `enabled=true`
- When: cycle 실행
- Then: `checkNewNews` 스킵 (가격 체크만)

### A8. 가격 알림 url 확장 (FR-10)
- Given: 가격 변동 임계값 돌파
- When: SEND
- Then: Push payload 에 `url="/stocks/AAPL"` 포함, 클릭 시 종목 상세 이동

---

## 8. Non-Goals / Deferred (재확인)

- 뉴스 요약 품질 개선 (별도 feature)
- 뉴스 개인화/랭킹/sentiment 필터
- 뉴스 알림 빈도 사용자 커스터마이징 (시스템 고정 15분)
- 뉴스 push 실패 시 외부 알림(email) fallback

---

## 9. 파일 변경 요약

| 파일 | 종류 | 변경 |
|------|:----:|------|
| `V12__notification_news.sql` | 신규 | `ADD COLUMN IF NOT EXISTS last_news_published_at TIMESTAMPTZ` |
| `NotificationSettingEntity.java` | 수정 | 필드 + getter + `markNewsNotified(Instant)` |
| `NotificationNewsDedupPolicy.java` | 신규 | 순수 함수 + Decision/Action enum |
| `NotificationCheckService.java` | 수정 | `NewsService` 주입 + `checkNewNews()` + `check()` 루프 확장 |
| `PushService.java` | 수정 | `sendToUser` 4-arg 오버로드 + `buildPayload()` 분리 |
| `NotificationNewsDedupPolicyTest.java` | 신규 | N1~N6 |
| `NotificationSettingEntityTest.java` | 수정 | U6~U7 추가 |
| `PushServiceTest.java` | 신규/수정 | P1~P4 (buildPayload 직접 검증) |
| `apps/web/public/sw.js` | **변경 없음** | 이미 url 지원 |

---

## 10. Implementation Order (Do Phase 가이드)

1. **V12 migration** 작성 → Flyway history 에 예약
2. **Entity** 필드·getter·`markNewsNotified()` 추가 + U6/U7 테스트
3. **NotificationNewsDedupPolicy** 순수 함수 + N1~N6 테스트
4. **PushService** 4-arg 오버로드 + `buildPayload` 분리 + P1~P4 테스트
5. **NotificationCheckService** `NewsService` 주입, `checkNewNews()` 구현, `check()` 루프 연결
6. **가격 알림 url 추가** (FR-10) — `checkPriceThreshold` SEND 시 `/stocks/{ticker}` 전달
7. `./gradlew check` — 모든 기존/신규 테스트 green
8. (optional) Zero Script QA 수동 검증 시나리오 (A1~A4, A6)
9. 커밋 → push → PR → squash merge → archive

---

## Version History

| Version | Date | Changes | Author |
|---|---|---|---|
| 1.0 | 2026-04-20 | 초기 Design — Policy 순수 함수 / Entity watermark / PushService url 오버로드 / FE 변경 0 / FR-10 (가격 알림 url) 추가 식별 | wonseok-han |
