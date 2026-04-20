---
template: analysis
version: 1.2
feature: notification-ui-cleanup
date: 2026-04-20
author: wonseok-han (via bkit:gap-detector)
project: AI Stock Advisor
status: Complete
---

# notification-ui-cleanup Gap Analysis

> **Summary**: Design vs 실제 구현 비교. Match Rate **100%**, 갭 없음.
>
> **Plan**: [notification-ui-cleanup.plan.md](../01-plan/features/notification-ui-cleanup.plan.md)
> **Design**: [notification-ui-cleanup.design.md](../02-design/features/notification-ui-cleanup.design.md)
> **Implementation Commit**: `bcb1507` on `feat/notification-ui-cleanup` (PR #13 → develop)
> **Date**: 2026-04-20

---

## Executive Summary

| 항목 | 값 |
|---|---|
| **Match Rate** | **100%** |
| **Missing items** | 0 |
| **Added items** | 1 (non-blocking — `title` 속성 추가, UX 개선 마이너 확장) |
| **Changed items** | 0 |
| **Residual `onSignalChange` in `apps/`** | 0 (완전 제거) |
| **Residual `on_signal_change` in `apps/`** | 2 (V7 원본 + V11 DROP — Flyway 히스토리 기대값) |
| **Invariants preserved** | `NotificationCheckService`, `NotificationDedupPolicy`, `PushService` 불변 / `onNewNews` 유지 |
| **Build** | BE `./gradlew check` ✅ / FE `tsc --noEmit` ✅ / `pnpm lint` 0 errors |
| **Recommendation** | Proceed to `/pdca report notification-ui-cleanup` |

---

## 1. FR Coverage (Plan §3.1)

| FR | 요구사항 | 구현 위치 | 상태 |
|----|---------|----------|:----:|
| FR-01 | 북마크 버튼 아이콘 전용 + `aria-label` | `bookmark-button.tsx:33-45` (`★`/`☆` 단일, aria-label 분기) | ✅ |
| FR-02 | 알림 버튼 아이콘 전용 | `notification-button.tsx:31-54` (종 SVG 만) | ✅ |
| FR-03 | 활성 상태 색 분기 (blue) | `notification-button.tsx:12-19, 33-37, 43` (`useNotificationSettings()` + `useMemo` + `fill="currentColor"`) | ✅ |
| FR-04 | 모달 시그널 토글 제거 | `notification-setting-modal.tsx:95-99` (ToggleRow 1개만) | ✅ |
| FR-05 | 마이페이지/리스트 시그널 UI 제거 | `my-page/notification-section.tsx`, `notification/notification-settings.tsx` (시그널 블록 부재) | ✅ |
| FR-06 | BE DTO에서 `onSignalChange` 필드 제거 | `Request.java`, `Response.java`, `Entity.java:23-42` (3 필드만) | ✅ |
| FR-07 | `on_signal_change` 컬럼 삭제 | `V11__drop_on_signal_change.sql` (`DROP COLUMN IF EXISTS`) | ✅ |
| FR-08 | 기존 데이터 보존 | V11 범위는 단일 컬럼만 | ✅ |

## 2. Design §2 (Axis A — UI Polish) 대조

| Design 명세 | 구현 | 상태 |
|---|---|:---:|
| BookmarkButton 텍스트 제거, 아이콘만 | `bookmark-button.tsx:44` `{isBookmarked ? '★' : '☆'}` | ✅ |
| `px-2 py-1.5 text-base leading-none` | `bookmark-button.tsx:36` | ✅ |
| NotificationButton 훅으로 활성 상태 조회 | `notification-button.tsx:12` | ✅ |
| `isActive = settings?.some(s.ticker === ticker && s.enabled)` | `notification-button.tsx:16-19` useMemo | ✅ |
| 활성 시 종 `fill="currentColor"` | `notification-button.tsx:43` | ✅ |
| 활성 색 blue (bookmark=yellow 와 구분) | `bg-blue-100 text-blue-700 ...` | ✅ |
| `aria-label` 상태별 분기 | `notification-button.tsx:38` | ✅ |

## 3. Design §3 (Axis C — `onSignalChange` 제거) 대조

| Design 명세 | 구현 | 상태 |
|---|---|:---:|
| §3.1 modal prop/state/ToggleRow 제거 | grep 0건 | ✅ |
| §3.2 `field` 유니언 축소, 시그널 ToggleChip 제거 | `notification-settings.tsx:26` `'onNewNews' \| 'enabled'` | ✅ |
| §3.3 마이페이지 시그널 배지 제거 | `notification-section.tsx` | ✅ |
| §3.4 types 3필드만 | `types/notification.ts:1-13` | ✅ |
| §3.5 Entity 필드·getter·`update()` 3-인자 | `NotificationSettingEntity.java:64-73` | ✅ |
| §3.6 Request/Response record 3필드 | 두 파일 완전 일치 | ✅ |
| §3.7 Service 업데이트 | `NotificationSettingService.java:34, 45-49` | ✅ |
| §3.8 EntityTest U1~U5 3-인자 | `NotificationSettingEntityTest.java:20, 32, 43, 55, 77` | ✅ |
| §3.9 V11 migration `DROP COLUMN IF EXISTS` | `V11__drop_on_signal_change.sql:5-6` | ✅ |

---

## 4. Residual Reference Check

### 4.1 Application Code (`apps/`)

| 검색어 | 매치 수 | 평가 |
|---|:---:|---|
| `onSignalChange` | 0 | ✅ 완전 제거 |
| `isOnSignalChange` | 0 | ✅ 완전 제거 |
| `on_signal_change` | 2 | ✅ V7 원본 CREATE + V11 DROP (Flyway 원칙) |
| `signalChange` | 0 | ✅ |

### 4.2 Out-of-Scope (후속 권장, 이번 PDCA 범위 밖)

| 파일 | 상황 | 권장 |
|---|---|---|
| `docs/planning/02-features.md:125` "AI 시그널 변화 시" | Phase 0 초기 기획 고정본 | `docs/planning/` 수정은 신중히 — 추후 시그널 알림 재도입 시 정리 |
| `docs/planning/03-architecture.md:129` `on_signal_change BOOLEAN` | 초기 아키텍처 스키마 예시 | 동일 |
| `docs/planning/06-roadmap.md:123` "시그널 변화" | 로드맵 항목 | 동일 |
| `docs/archive/**` | 과거 기록 | 읽기 전용, 수정 금지 |

## 5. Invariant Preservation

| 파일 | 기대 | 결과 |
|---|---|:---:|
| `NotificationCheckService.java` | 불변 | ✅ grep 0건 |
| `NotificationDedupPolicy.java` | 불변 | ✅ grep 0건 |
| `PushService.java` | 불변 | ✅ grep 0건 |
| `onNewNews` (전 계층) | 유지 | ✅ `apps/` 내 8파일 18건 참조 존재 |
| V7 원본 migration | 불변 (Flyway 규칙) | ✅ |

## 6. Build / Test Status

| 검증 | 명령 | 결과 |
|---|---|:---:|
| BE | `./gradlew check` | ✅ BUILD SUCCESSFUL |
| FE type | `pnpm tsc --noEmit` | ✅ |
| FE lint | `pnpm lint` | ✅ 0 errors (기존 sw.js warning 1건 무관) |
| EntityTest U1~U5 (3-인자) | Gradle | ✅ |
| NotificationDedupPolicyTest T1~T9 | Gradle | ✅ Green 유지 |

---

## 7. Gaps

**없음.** FR-01~FR-08 전부, Design §2/§3 전부 명세대로 구현됨.

## 8. Non-Gap Notes

| # | 항목 | 분류 |
|---|---|:---:|
| N1 | `title` 속성 추가 (`bookmark-button.tsx:42`, `notification-button.tsx:39`) — Design 명시 없음, `aria-label` 동일 값 | **긍정적 확장** — hover tooltip UX 개선. 문서 갱신 불필요 수준의 마이너 개선 |
| N2 | `onNewNews` 토글 유지 (죽은 토글이지만 별도 `notification-news` feature 예정) | **의도 그대로** — Plan §2 Non-goal로 명시 |
| N3 | V7 원본 migration에 `on_signal_change BOOLEAN` 남아있음 | **Flyway 원칙** — 원본 불변, V11 에서 누적 제거로 해소 |

---

## 9. Quality Metrics

| 지표 | 값 | 기준 | 결과 |
|---|---|---|:---:|
| Match Rate | 100% | ≥90% | ✅ |
| `./gradlew check` | BUILD SUCCESSFUL | pass | ✅ |
| FE typecheck | pass | pass | ✅ |
| FE lint | 0 errors | 0 | ✅ |
| 빌드 경고 | 0 (무관 sw.js 1건 제외) | 0 | ✅ |
| 커밋/PR 정합성 | 1 커밋 (bcb1507) → PR #13 | clean | ✅ |

---

## 10. Recommendation

- 매칭률 ≥90% 달성 → **`/pdca report notification-ui-cleanup`** 로 완료 리포트 생성 권장
- 반복 개선(`/pdca iterate`) 불필요 (갭 0)
- PR #13 머지 후 archive 진행
- 후속 과제 (이번 feature 범위 외):
  - `notification-news` feature 로 `onNewNews` 실제 발송 로직 구현
  - `docs/planning/` 의 시그널 언급은 시그널 재도입 판단 시점에 일괄 정정

---

## Version History

| Version | Date | Changes | Author |
|---|---|---|---|
| 1.0 | 2026-04-20 | 초기 분석 — Match Rate 100%, 갭 없음, non-gap notes 3건 | bkit:gap-detector + wonseok-han |
