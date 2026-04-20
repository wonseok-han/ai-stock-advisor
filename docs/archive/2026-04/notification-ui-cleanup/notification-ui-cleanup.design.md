---
template: design
version: 1.2
feature: notification-ui-cleanup
date: 2026-04-20
author: wonseok-han
project: AI Stock Advisor
status: Draft
---

# notification-ui-cleanup Design

> **Plan**: [notification-ui-cleanup.plan.md](../../01-plan/features/notification-ui-cleanup.plan.md)

---

## 1. Architecture Overview

두 축의 독립 변경:

```
Axis A (UI Polish) — FE only
├─ bookmark-button.tsx     : 텍스트 제거
└─ notification-button.tsx : 텍스트 제거 + 활성 상태 색 (훅 추가)

Axis C (Dead Feature Removal) — Full stack
├─ FE: 4 파일에서 onSignalChange UI/prop/handler 제거
├─ FE: types/notification.ts 필드 제거
├─ BE: Entity/Request/Response/Service 필드·update() 인자 제거
├─ BE: EntityTest update() 호출 축소
└─ DB: Flyway V11 DROP COLUMN on_signal_change
```

두 축 간 의존성은 없으나 같은 모듈(notification)을 건드리므로 하나의 PR·커밋 단위로 처리.

---

## 2. Axis A — UI Polish 상세

### 2.1 BookmarkButton

**변경 전:**
```tsx
<span className="text-base">{isBookmarked ? '★' : '☆'}</span>
{isBookmarked ? '북마크됨' : '북마크'}
```

**변경 후:**
```tsx
<span className="text-base leading-none">{isBookmarked ? '★' : '☆'}</span>
```

- 외곽 버튼은 padding 을 `px-2 py-1.5` 로 축소(아이콘 전용 정사각 형태)
- `aria-label` 로 접근성 유지(이미 존재)
- `gap-1.5` 제거(콘텐츠 1개)

### 2.2 NotificationButton

**핵심 변경:** 훅으로 현재 티커의 활성 상태 조회 + 색 분기.

```tsx
'use client';
import { useMemo, useState } from 'react';
import { useAuth } from '@/features/auth/auth-provider';
import { useNotificationSettings } from '@/features/notification/hooks/use-notification-settings';
import { AuthGuardModal } from '@/features/auth/auth-guard-modal';
import { NotificationSettingModal } from '@/features/stock-detail/notification-setting-modal';

export function NotificationButton({ ticker }: { ticker: string }) {
  const { user } = useAuth();
  const { data: settings } = useNotificationSettings();
  const [showAuthModal, setShowAuthModal] = useState(false);
  const [showSettingModal, setShowSettingModal] = useState(false);

  const isActive = useMemo(
    () => settings?.some((s) => s.ticker === ticker && s.enabled) ?? false,
    [settings, ticker],
  );

  function handleClick() {
    if (!user) { setShowAuthModal(true); return; }
    setShowSettingModal(true);
  }

  return (
    <>
      <button
        onClick={handleClick}
        className={`inline-flex cursor-pointer items-center rounded-lg px-2 py-1.5 transition-colors ${
          isActive
            ? 'bg-blue-100 text-blue-700 hover:bg-blue-200 dark:bg-blue-900/30 dark:text-blue-400'
            : 'bg-zinc-100 text-zinc-600 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-700'
        }`}
        aria-label={isActive ? '알림 설정됨 (편집)' : '알림 설정'}
      >
        <svg className="h-4 w-4" fill={isActive ? 'currentColor' : 'none'} viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" d="M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0" />
        </svg>
      </button>
      {showAuthModal && <AuthGuardModal onClose={() => setShowAuthModal(false)} />}
      {showSettingModal && <NotificationSettingModal ticker={ticker} onClose={() => setShowSettingModal(false)} />}
    </>
  );
}
```

- 훅 `useNotificationSettings` 는 `enabled: !!user` 로 비로그인 시 요청 안 감 (기존 구현)
- `isActive` 판정: 해당 티커 설정 존재 + `enabled=true`
- 활성 시 `fill="currentColor"` 로 종 아이콘을 채움(bookmark 의 ★/☆ 와 같은 메타포)
- 색 팔레트는 bookmark 와 구분하기 위해 blue 계열 사용 (bookmark=yellow, 알림=blue)

### 2.3 접근성

| 상태 | aria-label |
|------|------------|
| 비활성 | "알림 설정" |
| 활성 | "알림 설정됨 (편집)" |

---

## 3. Axis C — onSignalChange 제거 상세

### 3.1 FE: notification-setting-modal.tsx

**제거:**
- `initialOnSignalChange` prop
- `const [onSignalChange, setOnSignalChange] = useState(...)`
- `<ToggleRow label="AI 시그널 변화 시" ...>`
- `upsertMutation.mutate({ ..., req: { ..., onSignalChange, ... } })` 에서 `onSignalChange` 필드

남는 토글은 "새 뉴스 발생 시" 1개. `ToggleRow` 컴포넌트 자체는 유지.

### 3.2 FE: notification/notification-settings.tsx

**제거:**
- `field: 'onNewNews' | 'onSignalChange' | 'enabled'` → `field: 'onNewNews' | 'enabled'`
- `handleToggle` 본문에서 `onSignalChange:` 항목 제거
- `<ToggleChip label="시그널" ...>` 블록 제거

### 3.3 FE: my-page/notification-section.tsx

**제거:**
- `{s.onSignalChange && <span className="...">시그널</span>}` 배지 블록

### 3.4 FE: types/notification.ts

```ts
export interface NotificationSetting {
  ticker: string;
  priceChangeThreshold: number | null;
  onNewNews: boolean;
  enabled: boolean;
}

export interface NotificationSettingRequest {
  priceChangeThreshold: number | null;
  onNewNews: boolean;
  enabled: boolean;
}
```

### 3.5 BE: NotificationSettingEntity.java

**제거 필드:**
```java
@Column(nullable = false)
private boolean onSignalChange;
```

**업데이트된 `update()` 시그니처:**
```java
public void update(BigDecimal priceChangeThreshold, boolean onNewNews, boolean enabled) {
    boolean thresholdChanged = !Objects.equals(this.priceChangeThreshold, priceChangeThreshold);
    this.priceChangeThreshold = priceChangeThreshold;
    this.onNewNews = onNewNews;
    this.enabled = enabled;
    if (thresholdChanged) {
        this.lastTriggeredAbove = false;
        this.lastNotifiedAt = null;
    }
}
```

**제거 getter:** `isOnSignalChange()`.

### 3.6 BE: NotificationSettingRequest.java / Response.java

```java
public record NotificationSettingRequest(
        BigDecimal priceChangeThreshold,
        boolean onNewNews,
        boolean enabled
) {}

public record NotificationSettingResponse(
        String ticker,
        BigDecimal priceChangeThreshold,
        boolean onNewNews,
        boolean enabled
) {}
```

### 3.7 BE: NotificationSettingService.java

- `entity.update(req.priceChangeThreshold(), req.onNewNews(), req.enabled())` 로 인자 축소
- `toResponse()` 에서 `e.isOnSignalChange()` 제거

### 3.8 BE: NotificationSettingEntityTest.java

U1~U5 테스트의 `e.update(...)` 호출을 3-인자로 축소. 시그널 관련 가드 없음.

```java
// U1 before: e.update(new BigDecimal("5"), false, false, true);
// U1 after:  e.update(new BigDecimal("5"), false, true);
```

U2 테스트는 `isOnSignalChange()` 가드가 없었으므로 인자만 축소.

### 3.9 DB: V11__drop_on_signal_change.sql

```sql
-- Phase 4.5.2: 죽은 토글 제거 (notification-ui-cleanup)
-- on_signal_change 컬럼은 실제 발송 로직이 구현되지 않아 UI에서 제거되었음.
-- 향후 AI 시그널 알림이 필요해지면 별도 migration 으로 재도입.

ALTER TABLE notification_settings
    DROP COLUMN IF EXISTS on_signal_change;
```

**멱등성:** `IF EXISTS` 로 이미 삭제된 환경에서도 무해.
**순서:** V10 이 존재하므로 V11 로 지정.
**롤백:** 필요 시 `ALTER TABLE notification_settings ADD COLUMN on_signal_change BOOLEAN NOT NULL DEFAULT false;` — 데이터 유실되나 기능이 없었으므로 실질 영향 없음.

---

## 4. Implementation Order

| # | 단계 | 파일 | 이유 |
|---|------|------|------|
| 1 | V11 migration 작성 | `db/migration/V11__drop_on_signal_change.sql` | Entity 수정 전 DB 스키마 먼저 결정 |
| 2 | BE Entity 수정 | `NotificationSettingEntity.java` | 필드·update() 시그니처 축소 |
| 3 | BE DTO 수정 | `Request.java`, `Response.java` | record 간단 |
| 4 | BE Service 업데이트 | `NotificationSettingService.java` | Entity/DTO 변경 반영 |
| 5 | BE Entity 테스트 수정 | `NotificationSettingEntityTest.java` | 컴파일 에러 해소 |
| 6 | BE 빌드 | `./gradlew check` | 2~5 검증 |
| 7 | FE types 수정 | `types/notification.ts` | 타입부터 정리 |
| 8 | FE 시그널 UI 제거 | modal · settings · section | 3 파일 동시 |
| 9 | FE UI 개선(A) | bookmark-button, notification-button | 독립 변경이나 같은 PR |
| 10 | FE 빌드 | `make web-check` | 7~9 검증 |
| 11 | 커밋 → push → PR | | |

---

## 5. Test Coverage

### 5.1 자동 테스트

| 테스트 | 스코프 | 변경 |
|--------|--------|------|
| `NotificationSettingEntityTest.U1~U5` | Entity | 호출부 인자 3개로 축소 |
| `NotificationDedupPolicyTest.T1~T9` | Policy | 영향 없음 (그대로 green 유지) |

### 5.2 수동 확인 (Zero Script QA)

| # | 시나리오 | 기대 |
|---|----------|------|
| M1 | 로그인 후 종목 상세에서 알림 버튼 클릭 → 임계값 설정 저장 | 버튼이 중립색 → 활성(blue)색으로 전환 |
| M2 | 다시 페이지 열어도 활성 색 유지 | React Query 캐시 hit |
| M3 | 설정 모달에서 "알림 해제" | 버튼이 다시 중립색으로 돌아옴 |
| M4 | 모달에 토글은 "새 뉴스 발생 시" 1개만 존재 | "AI 시그널 변화 시" 토글 없음 |
| M5 | 마이페이지 알림 섹션에 "시그널" 배지 없음 | 뉴스·비활성 배지만 |
| M6 | DB 에 직접 `\d notification_settings` → `on_signal_change` 컬럼 없음 | V11 적용 확인 |

---

## 6. Non-Gap Notes (미리 정의)

- **`onNewNews` 토글은 유지**: 죽은 토글이지만 가까운 시일 내 `notification-news` feature 에서 구현 예정. 삭제하지 않음.
- **`NotificationCheckService` 불변**: 시그널 알림 로직이 애초에 없었으므로 수정 불필요.
- **`notification.push-prompt.tsx` 불변**: 구독 프롬프트는 시그널과 무관.

---

## 7. Risks & Mitigations

| 리스크 | 완화 |
|--------|------|
| 누락된 `onSignalChange` 참조로 빌드 에러 | Plan 에서 grep 결과 기반 체크리스트(17 파일 중 영향 7 파일) 확보. 빌드 단계에서 즉시 발견 |
| 프로덕션 DB 에 적재된 `on_signal_change=true` 데이터 유실 | 기능 미구현 상태였으므로 UX 영향 없음 — 리스크 수용 |
| React Query 캐시 무효화 누락으로 알림 버튼 상태 stale | 모달의 `useUpsertNotificationSetting` / `useDeleteNotificationSetting` 이 이미 `['notification-settings']` 무효화 |

---

## Version History

| Version | Date | Changes | Author |
|---|---|---|---|
| 1.0 | 2026-04-20 | 초기 Design — A+C 구현 명세 | wonseok-han |
