---
template: design
version: 1.2
feature: notification-dedup
date: 2026-04-20
author: wonseok-han
project: AI Stock Advisor
status: Draft
---

# notification-dedup Design Document

> **Summary**: 가격 변동 푸시의 중복 발송을 히스테리시스 + 쿨다운으로 억제하기 위한 상태 기반 체크 로직 설계.
>
> **Project**: AI Stock Advisor
> **Version**: Phase 4.5 post
> **Author**: wonseok-han
> **Date**: 2026-04-20
> **Status**: Draft
> **Planning Doc**: [notification-dedup.plan.md](../../01-plan/features/notification-dedup.plan.md)

---

## 1. Overview

### 1.1 Design Goals

1. **상태 전이를 알림의 1급 개념으로 도입** — "임계값 초과" 라는 순간 상태가 아니라 "아래→위 전이"를 트리거로 삼는다.
2. **경계 진동에 강건** — 리셋 임계값을 트리거보다 낮게 두어(히스테리시스) 4.9%↔5.1% 같은 진동에서도 단 1회만 발송되도록 한다.
3. **발송 실패에 안전** — 푸시 발송이 실제로 성공한 경우에만 "발송됨" 상태를 저장하여, 일시 실패 시 다음 사이클에서 재시도될 수 있게 한다.
4. **런타임 튜닝 가능** — 리셋 비율, 쿨다운 시간은 `application.yml` 로 주입, 재배포 없이 profile로 교체 가능.

### 1.2 Design Principles

- **SRP** — `NotificationCheckService` 는 "체크 + 디스패치", 상태 판정 로직은 순수 함수로 분리.
- **순수 함수 우선** — 상태 전이 결정(`Decision`)은 현재값·임계값·마지막 상태만 입력받는 순수 함수로 작성 → 단위 테스트가 DB 없이 가능.
- **Fail-safe** — 예외 발생 시 "발송 안 함 + 상태 유지"가 기본 동작. 과소 발송이 과잉 발송보다 낫다는 원칙.
- **Backward compatible migration** — 기존 `notification_settings` 데이터는 `last_triggered_above=false`, `last_notified_at=NULL` 로 초기화되어 첫 사이클에서 정상 동작.

---

## 2. Architecture

### 2.1 Component Diagram

```
┌─────────────────────────┐
│ @Scheduled (15min)      │
│ NotificationCheckService│
└───────────┬─────────────┘
            │ (1) loop unique tickers
            ▼
┌─────────────────────────┐   ┌──────────────────────┐
│ QuoteService.getQuote() │──▶│ Quote (changePercent)│
└─────────────────────────┘   └──────────────────────┘
            │
            │ (2) for each setting
            ▼
┌─────────────────────────────────────────────┐
│ NotificationDedupPolicy.decide(...)         │  ◀── 순수 함수
│   in : (absChange, threshold, resetRatio,   │       단위 테스트 대상
│         cooldownHours, lastTriggeredAbove,  │
│         lastNotifiedAt, now)                │
│   out: Decision { action, nextState }       │
│     action ∈ {SEND, SKIP_NO_TRANSITION,     │
│               SKIP_COOLDOWN, RESET_ONLY}    │
└──────────┬──────────────────────────────────┘
           │
           ├─ action=SEND ────▶ PushService.sendToUser()  (boolean)
           │                     │
           │                     ▼ success?
           │                     ├─ true  → Entity.markNotified(now)
           │                     └─ false → no state change (retry next cycle)
           │
           ├─ action=RESET_ONLY ▶ Entity.resetTrigger()
           │
           └─ action=SKIP_*   ──▶ log.debug(reason)
```

### 2.2 Data Flow

```
[Scheduler] 
  → findAll(enabled=true)
  → group by ticker, fetch Quote
  → for each (setting, quote):
      Decision d = policy.decide(...)
      switch d.action:
        SEND:
          if pushService.sendToUser(userId, title, body) == true:
            setting.markNotified(now)        // lastNotifiedAt=now, lastTriggeredAbove=true
            repo.save(setting)
          // else: 상태 변경 없음
        RESET_ONLY:
          setting.resetTrigger()              // lastTriggeredAbove=false
          repo.save(setting)
        SKIP_NO_TRANSITION | SKIP_COOLDOWN:
          log.debug(...)
```

### 2.3 Dependencies

| Component | Depends On | Purpose |
|---|---|---|
| `NotificationCheckService` | `NotificationSettingRepository`, `QuoteService`, `PushService`, `NotificationDedupPolicy`, `NotificationDedupProperties` | 오케스트레이션 |
| `NotificationDedupPolicy` | — (순수 함수) | 상태 전이 결정 |
| `NotificationDedupProperties` | Spring `@ConfigurationProperties` | 리셋 비율·쿨다운 설정 주입 |
| `PushService` | `PushSubscriptionRepository`, `webpush` 라이브러리 | 푸시 발송. **반환 타입 void → boolean 으로 변경** |
| `NotificationSettingService` | `NotificationSettingRepository` | 사용자 설정 변경 시 상태 리셋 경로 제공 |

---

## 3. Data Model

### 3.1 Entity Definition

**`NotificationSettingEntity`** (기존) — 2개 필드 추가.

```java
@Entity
@Table(name = "notification_settings")
public class NotificationSettingEntity {
    // ... 기존 필드 유지 (id, userId, ticker, priceChangeThreshold,
    //     onNewNews, onSignalChange, enabled)

    @Column(name = "last_notified_at")
    private OffsetDateTime lastNotifiedAt;        // NULL 허용. 발송 성공 시점 기록

    @Column(name = "last_triggered_above", nullable = false)
    private boolean lastTriggeredAbove = false;   // 현재 임계값 위 상태 유지 여부

    // 상태 조작용 public 메서드 (setter 대신)
    public void markNotified(OffsetDateTime now) {
        this.lastNotifiedAt = now;
        this.lastTriggeredAbove = true;
    }

    public void resetTrigger() {
        this.lastTriggeredAbove = false;
    }

    // 기존 update() 메서드는 임계값 변경 감지 + 상태 리셋 로직 추가
    public void update(BigDecimal priceChangeThreshold, boolean onNewNews,
                       boolean onSignalChange, boolean enabled) {
        boolean thresholdChanged =
            !Objects.equals(this.priceChangeThreshold, priceChangeThreshold);
        this.priceChangeThreshold = priceChangeThreshold;
        this.onNewNews = onNewNews;
        this.onSignalChange = onSignalChange;
        this.enabled = enabled;
        if (thresholdChanged) {
            this.lastTriggeredAbove = false;
            this.lastNotifiedAt = null;
        }
    }
}
```

> `OffsetDateTime` 사용 이유 — PostgreSQL `TIMESTAMPTZ` 와 정확한 매핑, 프로젝트 기존 `jdbc.time_zone=UTC` 정책과 일치.

### 3.2 Entity Relationships

변경 없음. `notification_settings` 는 독립 테이블이며 `user_id`, `ticker` 를 가짐.

### 3.3 Database Schema

**Migration: `V8__notification_dedup.sql`**

```sql
-- Phase 4.5+: 가격 알림 중복 억제(히스테리시스 + 쿨다운)용 상태 컬럼

ALTER TABLE notification_settings
    ADD COLUMN last_notified_at TIMESTAMPTZ,
    ADD COLUMN last_triggered_above BOOLEAN NOT NULL DEFAULT false;

-- 주: ADD COLUMN DEFAULT 는 PostgreSQL 11+ 에서 fast default로 처리되어
--    테이블 rewrite 없이 즉시 반영됨.
-- 기존 레코드는 자동으로 (NULL, false) 로 초기화되며,
-- 첫 체크 사이클에서 조건 충족 시 정상적으로 1회 발송 후 상태 저장.
```

**검증 쿼리:**
```sql
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'notification_settings'
  AND column_name IN ('last_notified_at', 'last_triggered_above');
```

---

## 4. Core Logic Specification

### 4.1 상태 전이 다이어그램

```
                  ┌─────────────────────────────┐
                  │ state: lastTriggeredAbove   │
                  │        + lastNotifiedAt     │
                  └─────────────────────────────┘

       (false, *)                              (true, t0)
          │                                         │
          │ absChange >= threshold                  │
          │ ──────────────────────▶  [DECISION]    │
          │         SEND                            │
          │         (발송 성공 시)                   │
          │                                         │
          │         ┌───────────────────────────────┘
          │         │
          │         │  absChange >= threshold (계속 위)
          │         │  AND now - t0 < cooldown
          │         │  ──▶ SKIP_NO_TRANSITION
          │         │
          │         │  absChange >= threshold (계속 위)
          │         │  AND now - t0 >= cooldown
          │         │  ──▶ SKIP_NO_TRANSITION
          │         │       (히스테리시스로 여전히 "위" 상태이므로 미발송)
          │         │
          │         │  absChange < threshold*resetRatio
          │         │  ──▶ RESET_ONLY  (lastTriggeredAbove → false)
          │         │
          │         ▼
          │     (true, t0)
          │         │
          │         │  RESET_ONLY 이후 → (false, t0)
          │         │
          └─────────┘
                      (false, t0) + absChange >= threshold:
                        - now - t0 >= cooldown → SEND (새 사이클 시작)
                        - now - t0 < cooldown  → SKIP_COOLDOWN
```

### 4.2 Decision 의사결정 규칙 (순수 함수)

```java
public final class NotificationDedupPolicy {

    public enum Action { SEND, SKIP_NO_TRANSITION, SKIP_COOLDOWN, RESET_ONLY, NOOP }

    public record Decision(Action action, String reason) {}

    public static Decision decide(
            BigDecimal absChange,           // |quote.changePercent|
            BigDecimal threshold,           // setting.priceChangeThreshold
            BigDecimal resetRatio,          // e.g. 0.6
            Duration cooldown,              // e.g. 4h
            boolean lastTriggeredAbove,
            OffsetDateTime lastNotifiedAt,  // nullable
            OffsetDateTime now
    ) {
        BigDecimal resetThreshold = threshold.multiply(resetRatio);
        boolean aboveTrigger = absChange.compareTo(threshold) >= 0;
        boolean belowReset = absChange.compareTo(resetThreshold) < 0;

        if (aboveTrigger && !lastTriggeredAbove) {
            // 전이: 아래 → 위
            if (lastNotifiedAt != null
                && Duration.between(lastNotifiedAt, now).compareTo(cooldown) < 0) {
                return new Decision(Action.SKIP_COOLDOWN,
                    "cooldown active: last=" + lastNotifiedAt);
            }
            return new Decision(Action.SEND, "transition below→above");
        }

        if (aboveTrigger /* && lastTriggeredAbove */) {
            return new Decision(Action.SKIP_NO_TRANSITION, "still above, already notified");
        }

        if (belowReset && lastTriggeredAbove) {
            return new Decision(Action.RESET_ONLY, "dropped below reset threshold");
        }

        return new Decision(Action.NOOP, "between reset and trigger, no change");
    }
}
```

**핵심 불변식:**
- `aboveTrigger ∧ ¬lastTriggeredAbove` 에서만 SEND 가능 → 상태 전이 1회 원칙.
- SEND 이후 `lastTriggeredAbove=true` 로 바뀌므로 같은 상승을 유지하는 한 재발송 없음.
- `RESET_ONLY` 는 `lastNotifiedAt` 을 건드리지 않음 → 쿨다운 타이머가 리셋 시점이 아닌 발송 시점 기준 유지됨.

### 4.3 설정 값

**`NotificationDedupProperties`**

```java
@ConfigurationProperties(prefix = "app.notification.dedup")
public record NotificationDedupProperties(
        BigDecimal resetRatio,    // default 0.6
        Duration cooldown         // default PT4H
) {
    public NotificationDedupProperties {
        if (resetRatio == null) resetRatio = new BigDecimal("0.6");
        if (cooldown == null)   cooldown   = Duration.ofHours(4);
    }
}
```

**`application.yml` 추가:**

```yaml
app:
  notification:
    dedup:
      reset-ratio: ${NOTIFICATION_DEDUP_RESET_RATIO:0.6}
      cooldown: ${NOTIFICATION_DEDUP_COOLDOWN:PT4H}
```

> `app.*` 네임스페이스는 프로젝트 컨벤션(기존 `app.push`, `app.cache` 등)과 일치. Plan 문서의 `notification.dedup` 표기는 여기서 `app.notification.dedup` 으로 정정.

### 4.4 `PushService.sendToUser()` 시그니처 변경

**Before:**
```java
public void sendToUser(UUID userId, String title, String body)
```

**After:**
```java
public boolean sendToUser(UUID userId, String title, String body)
// return:
//   true  = 구독자 1명 이상에게 최소 1건 발송 성공
//   false = webPushService 미초기화, 구독자 0명, 혹은 모든 시도 실패
```

**구현 변경 포인트 (`PushService.java:70-90`):**
```java
public boolean sendToUser(UUID userId, String title, String body) {
    if (webPushService == null) { /* ... */ return false; }
    List<PushSubscriptionEntity> subs = subscriptionRepo.findByUserId(userId);
    if (subs.isEmpty()) return false;

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
```

> **영향 범위:** `PushService.sendToUser()` 는 현재 `NotificationCheckService` 와 `AccountService` (auth) 에서 호출됨(Grep 기반). `AccountService` 호출부는 반환값을 무시해도 무방(기존 동작 유지).

---

## 5. Public API

사용자/관리자 노출 API는 **변경 없음**. 내부 서비스 메서드 시그니처 변경만 존재.

| Layer | Method | Before | After |
|---|---|---|---|
| Service | `PushService.sendToUser` | `void` | `boolean` |
| Service | `NotificationCheckService.check` | 동일 | 동일 |
| Service | `NotificationSettingService.upsert` | 동일 | 동일 (내부적으로 Entity.update 가 임계값 변경 감지) |

REST 엔드포인트 변경 없음 → FE 변경 불필요.

---

## 6. Error Handling

| 상황 | 처리 |
|---|---|
| `QuoteService.getQuote()` 예외 | 기존과 동일하게 `log.debug` 후 해당 ticker skip |
| `PushService.sendToUser()` 가 false 반환 (모든 구독자 실패) | 상태 변경하지 않음 → 다음 사이클에 동일 Decision.SEND 재시도 (단, 쿨다운 타이머는 시작 안 함) |
| DB 저장 실패 | 트랜잭션 롤백. 상태 변경 미적용 → 다음 사이클 재시도 |
| `last_notified_at` 이 미래 시각(서버 시계 뒤로 감김) | `Duration.between` 은 음수 반환 → `compareTo(cooldown) < 0` 참 → SKIP_COOLDOWN (보수적 선택) |
| 사용자가 임계값을 0이나 음수로 저장 | Entity `update()` 에서 검증 추가 (권장: `BeanValidation` 으로 `@DecimalMin(value="0.0", inclusive=false)`) — 범위 외 이슈이므로 본 feature에서는 미포함, 후속 과제 |

---

## 7. Security Considerations

- 본 feature 는 내부 스케줄러 로직 변경. 새로운 외부 입력 없음 → 보안 표면 변화 없음.
- `application.yml` 설정값은 환경 변수로 override 가능하되 기본값이 안전.
- 푸시 payload 구성은 기존 로직 그대로 (title/body escape 처리 유지).

---

## 8. Test Plan

### 8.1 Test Scope

| Type | Target | Tool |
|---|---|---|
| Unit | `NotificationDedupPolicy.decide` (순수 함수) | JUnit 5 |
| Unit | `NotificationSettingEntity.update` 의 임계값 변경 시 리셋 | JUnit 5 |
| Unit | `PushService.sendToUser` boolean 반환 | JUnit 5 + Mockito |
| Integration | `NotificationCheckService.check` 전체 루프 (스케줄러 직접 호출) | `@SpringBootTest` + Testcontainers (기존 `TestcontainersConfiguration` 재사용) |

### 8.2 Test Cases (Key)

**`NotificationDedupPolicyTest`** — 순수 함수 (DB·Mock 불필요):

| # | Given | When | Then | Action |
|---|---|---|---|---|
| T1 | `absChange=3%, threshold=5%, lastAbove=false, lastAt=null` | decide | NOOP | — |
| T2 | `absChange=5%, threshold=5%, lastAbove=false, lastAt=null` | decide | **SEND** | transition below→above |
| T3 | `absChange=5.3%, threshold=5%, lastAbove=true, lastAt=now-30min` | decide | **SKIP_NO_TRANSITION** | 유지 상태 |
| T4 | `absChange=4.9%, threshold=5%, reset=0.6, lastAbove=true` (resetThr=3%) | decide | NOOP | 리셋 구간 이전 (진동 억제) |
| T5 | `absChange=2.5%, threshold=5%, reset=0.6, lastAbove=true` | decide | **RESET_ONLY** | reset 이하 |
| T6 | `absChange=5.1%, threshold=5%, lastAbove=false, lastAt=now-1h, cooldown=4h` | decide | **SKIP_COOLDOWN** | 재돌파 but 쿨다운 중 |
| T7 | `absChange=5.1%, threshold=5%, lastAbove=false, lastAt=now-5h, cooldown=4h` | decide | **SEND** | 쿨다운 만료 후 재돌파 |
| T8 | `absChange=7%, lastAt=(future time)` (시계 역전) | decide | **SKIP_COOLDOWN** | fail-safe |

**`NotificationSettingEntityTest`**:
- U1: 임계값 5 → 7 변경 시 `lastTriggeredAbove` 가 false로, `lastNotifiedAt` 이 null로 리셋됨.
- U2: 임계값 동일값으로 재저장 시 상태 유지.
- U3: 기타 필드(enabled 등) 변경 시 상태 유지.

**`NotificationCheckServiceIntegrationTest`** (최소한):
- I1: DB에 이미 `lastTriggeredAbove=true` 인 setting 이 있을 때 같은 초과 값에서 push 호출 0회.
- I2: 신규 setting 에서 임계값 돌파 1회 발생 시 `PushService.sendToUser` 1회 호출 + DB 에 상태 저장.
- I3: push 가 false 반환하면 DB 상태 변경 없음 (다음 호출에서 재시도 가능).

---

## 9. Clean Architecture

### 9.1 Layer Structure (본 feature 한정)

| Layer | Responsibility | Location |
|---|---|---|
| **Application** | 스케줄링, 오케스트레이션 | `notification/service/NotificationCheckService.java` |
| **Domain (policy)** | 상태 전이 의사결정 (순수 함수) | `notification/service/NotificationDedupPolicy.java` (new) |
| **Domain (entity)** | 상태 필드 + 변경 규칙 | `notification/infra/NotificationSettingEntity.java` (수정) |
| **Infrastructure** | JPA Repository, PostgreSQL, webpush | `notification/infra/*`, `db/migration/V8__*.sql` |
| **Config** | 설정 바인딩 | `notification/config/NotificationDedupProperties.java` (new) |

### 9.2 Dependency Rules

- `NotificationDedupPolicy` 는 JPA·Spring 의존성 **0개** (외부 라이브러리 없이 순수 Java).
- `NotificationCheckService` 는 Policy 를 정적 호출 (DI 불필요, 메서드가 static).
- Properties 는 `@ConfigurationProperties` 로 주입되고 CheckService 생성자에서 수신.

### 9.3 This Feature's Layer Assignment

| Component | Layer | Location |
|---|---|---|
| `NotificationDedupPolicy` | Domain | `apps/api/src/main/java/com/aistockadvisor/notification/service/NotificationDedupPolicy.java` |
| `NotificationDedupProperties` | Config | `apps/api/src/main/java/com/aistockadvisor/notification/config/NotificationDedupProperties.java` |
| `NotificationCheckService` | Application | 기존 파일 수정 |
| `NotificationSettingEntity` | Infra(JPA)/Domain 혼합 | 기존 파일 수정 |
| `V8__notification_dedup.sql` | Infra | `apps/api/src/main/resources/db/migration/` |

---

## 10. Coding Convention Reference

`CLAUDE.md` BE 섹션 준수:
- 패키지: `com.aistockadvisor.notification.*`
- 클래스: PascalCase — `NotificationDedupPolicy`, `NotificationDedupProperties`
- 메서드: camelCase — `decide`, `markNotified`, `resetTrigger`
- 상수: 본 feature 는 설정 주입으로 대체, 하드코딩 상수 없음.
- Flyway: `V8__notification_dedup.sql` (snake_case)

---

## 11. Implementation Guide

### 11.1 File Structure (변경/생성)

```
apps/api/src/main/java/com/aistockadvisor/notification/
├── config/
│   └── NotificationDedupProperties.java            [NEW]
├── service/
│   ├── NotificationDedupPolicy.java                [NEW]
│   ├── NotificationCheckService.java               [MODIFIED]
│   ├── NotificationSettingService.java             [(변경 없음 — Entity.update 가 처리)]
│   └── PushService.java                            [MODIFIED: void→boolean]
└── infra/
    └── NotificationSettingEntity.java              [MODIFIED]

apps/api/src/main/resources/
├── application.yml                                 [MODIFIED: app.notification.dedup 추가]
└── db/migration/
    └── V8__notification_dedup.sql                  [NEW]

apps/api/src/test/java/com/aistockadvisor/notification/
├── service/
│   ├── NotificationDedupPolicyTest.java            [NEW]
│   └── NotificationCheckServiceIntegrationTest.java [NEW, optional]
└── infra/
    └── NotificationSettingEntityTest.java          [NEW]
```

### 11.2 Implementation Order

1. [ ] `NotificationDedupPolicy` + `NotificationDedupPolicyTest` (순수 함수 먼저, TDD)
2. [ ] `NotificationDedupProperties` + `application.yml` 기본값
3. [ ] `V8__notification_dedup.sql` 작성 + 로컬 DB 마이그레이션 확인
4. [ ] `NotificationSettingEntity` — 2 필드 + `markNotified/resetTrigger/update(감지)`
5. [ ] `NotificationSettingEntityTest`
6. [ ] `PushService.sendToUser` → `boolean` 반환, 호출부 (`AccountService`, `NotificationCheckService`) 컴파일 확인
7. [ ] `NotificationCheckService` 재작성 — Policy 호출, Action 스위치, 발송 성공 시 상태 저장
8. [ ] 통합 확인: `./gradlew check` + 로컬 `make api-dev` 로 스케줄 실행 로그 확인
9. [ ] Zero Script QA: 더미 setting 으로 경계값 관찰 — `ticker=AAPL, threshold=0.1%` 설정 후 관측

### 11.3 Rollback Strategy

- V8 마이그레이션은 순수 ADD COLUMN → 문제 시 V9 에서 DROP COLUMN 가능.
- 코드 레벨 롤백은 이전 커밋 revert 로 가능하되, DB 스키마는 유지되어도 하위 호환(컬럼은 존재해도 읽지 않음).

---

## Version History

| Version | Date | Changes | Author |
|---|---|---|---|
| 0.1 | 2026-04-20 | Initial draft — 상태 전이 다이어그램, Decision 순수 함수, PushService 반환 타입 변경 포함 | wonseok-han |
