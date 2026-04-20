---
template: plan
version: 1.2
feature: notification-ui-cleanup
date: 2026-04-20
author: wonseok-han
project: AI Stock Advisor
status: Draft
---

# notification-ui-cleanup Plan

## Executive Summary

| 관점 | 요약 |
|---|---|
| **Problem** | 종목 상세 페이지 북마크/알림 버튼에 아이콘 옆 불필요한 텍스트 노출, 알림 설정 후에도 시각적 피드백 없어 "눌렀는지" 불명확. 또한 알림 설정 모달의 "AI 시그널 변화 시" 토글은 실제 발송 로직이 없는 **죽은 토글**이며, 기능 구현 시 Gemini 토큰 비용이 예산 대비 과함. |
| **Solution** | (A) 북마크/알림 버튼 텍스트 제거하고 아이콘만 유지. 알림 버튼은 현재 설정된 티커인지 `useNotificationSettings()` 로 조회해 활성 상태 색 분기. (C) `onSignalChange` 필드를 FE 타입·UI·BE DTO·Entity·DB 컬럼에서 완전히 제거 (Flyway V11 DROP COLUMN). |
| **Function UX Effect** | 버튼이 깔끔해지고, 알림 설정 후 북마크처럼 색 변화로 상태를 한눈에 확인. 모달의 불필요한 토글 제거로 사용자가 실제 동작하는 옵션(뉴스 알림)만 보게 됨. |
| **Core Value** | "참고용 분석 도구" 포지셔닝에 맞는 최소 UI + YAGNI 적용. 죽은 기능 삭제로 UX 부채 제거, 향후 필요해질 때 다시 추가 가능. |

## 1. Goal

- **G1 (UI)**: 북마크/알림 버튼을 아이콘 전용으로 정리하고, 알림 활성 상태를 시각 피드백(색)으로 제공.
- **G2 (기능 제거)**: `onSignalChange` 토글과 관련 필드/컬럼을 풀 스택에서 제거.
- **G3 (영향 최소화)**: 기존 알림 가격 임계값 플로우(`notification-dedup`) 및 뉴스 토글은 그대로 유지.

## 2. Non-Goals

- **뉴스 알림 실제 발송 구현**: 별도 feature(`notification-news`)로 분리. 이번 PDCA 범위 아님.
- **알림 UI 전면 재설계**: 현재 컴포넌트 구조 유지, 스타일만 소폭 조정.
- **AI 시그널 기능 자체 삭제**: on-demand AI 시그널 페이지/엔드포인트는 그대로 유지. "시그널 변화 알림"만 삭제.

## 3. Requirements

### 3.1 Functional Requirements

| FR | 요구사항 | 수용 기준 |
|----|---------|-----------|
| FR-01 | 북마크 버튼은 아이콘만 표시 | 버튼 텍스트 콘텐츠 없음, `aria-label` 로 접근성 유지 |
| FR-02 | 알림 버튼은 아이콘만 표시 | 동일, `aria-label="알림 설정"` 유지 |
| FR-03 | 알림 버튼은 현재 티커 알림 활성 시 색 분기 | 설정 존재 + `enabled=true` 면 활성 색(blue 계열), 아니면 중립 색 |
| FR-04 | 알림 설정 모달에서 "AI 시그널 변화 시" 토글 제거 | `notification-setting-modal.tsx` 에 `ToggleRow` 2개 → 1개(뉴스)로 축소 |
| FR-05 | 마이페이지 알림 섹션/리스트에서 "시그널" 배지·칩 제거 | `my-page/notification-section.tsx`, `notification/notification-settings.tsx` 에 시그널 표시 없음 |
| FR-06 | BE DTO(`NotificationSettingRequest`/`Response`)에서 `onSignalChange` 필드 제거 | 풀 정합 — Entity·Service·FE 타입 모두 제거 |
| FR-07 | `notification_settings.on_signal_change` 컬럼 삭제 | Flyway V11 migration `DROP COLUMN on_signal_change` |
| FR-08 | 기존 데이터 보존 | `on_new_news`, `price_change_threshold`, `enabled`, `last_notified_at`, `last_triggered_above` 영향 없음 |

### 3.2 Non-Functional Requirements

| NFR | 요구사항 |
|-----|---------|
| NFR-01 | FE typecheck + lint 통과 (`make web-check`) |
| NFR-02 | BE `./gradlew check` 통과 — 기존 Entity 테스트 `update()` 시그니처 변경에 맞춰 수정 |
| NFR-03 | Flyway migration 멱등성 — `DROP COLUMN IF EXISTS` 사용 |
| NFR-04 | PR 1개로 묶어 squash merge (feat/notification-ui-cleanup → develop) |

## 4. Scope & Impact

### 4.1 FE 변경 파일
| 파일 | 변경 |
|------|------|
| `apps/web/src/features/bookmark/bookmark-button.tsx` | 텍스트 제거, 아이콘만 (★/☆) |
| `apps/web/src/features/stock-detail/notification-button.tsx` | 텍스트 제거 + `useNotificationSettings()` 로 활성 상태 색 |
| `apps/web/src/features/stock-detail/notification-setting-modal.tsx` | 시그널 토글·관련 state·initial prop 제거 |
| `apps/web/src/features/notification/notification-settings.tsx` | `ToggleChip` 시그널 제거, `handleToggle` field 시그니처 축소 |
| `apps/web/src/features/my-page/notification-section.tsx` | "시그널" 배지 제거 |
| `apps/web/src/types/notification.ts` | `onSignalChange` 필드 제거 |

### 4.2 BE 변경 파일
| 파일 | 변경 |
|------|------|
| `apps/api/src/main/java/com/aistockadvisor/notification/infra/NotificationSettingEntity.java` | `onSignalChange` 필드/getter/`update()` 인자 제거 |
| `apps/api/src/main/java/com/aistockadvisor/notification/domain/NotificationSettingRequest.java` | 필드 제거 |
| `apps/api/src/main/java/com/aistockadvisor/notification/domain/NotificationSettingResponse.java` | 필드 제거 |
| `apps/api/src/main/java/com/aistockadvisor/notification/service/NotificationSettingService.java` | `update()` 호출·`toResponse()` 업데이트 |
| `apps/api/src/test/java/com/aistockadvisor/notification/infra/NotificationSettingEntityTest.java` | `update(...)` 호출부 인자 축소 |
| `apps/api/src/main/resources/db/migration/V11__drop_on_signal_change.sql` | **신규** — `ALTER TABLE notification_settings DROP COLUMN IF EXISTS on_signal_change;` |

### 4.3 영향받지 않는 부분
- `NotificationCheckService`, `NotificationDedupPolicy`, `PushService` — 시그널 알림 로직 자체가 없었으므로 수정 불필요
- `onNewNews` 필드 — 토글은 유지(별도 feature 에서 실제 구현 예정)
- V7, V10 마이그레이션 — 그대로

## 5. Risks

| 리스크 | 영향 | 완화 |
|--------|-----|------|
| 프로덕션 DB 에 이미 `on_signal_change=true` 설정이 저장된 사용자 | 데이터 손실이지만 기능이 없었으므로 실질 UX 손실 없음 | migration 전 사용자에게 공지 불필요 (죽은 토글이었음) |
| FE 타입 변경으로 다른 곳에서 컴파일 에러 | FE 빌드 실패 | `onSignalChange` grep → 발견된 17파일 중 위 목록으로 모두 커버. `make web-check` 로 확인 |
| `NotificationSettingEntity.update()` 시그니처 변경 | 테스트·Service 호출부 에러 | 호출부 2곳(Service + EntityTest)만 수정하면 됨 |

## 6. Success Criteria

- [ ] 종목 상세 페이지 북마크/알림 버튼에 텍스트 없음, 알림 버튼이 활성/비활성 색으로 구분됨
- [ ] 알림 설정 모달에 "AI 시그널 변화 시" 토글 없음
- [ ] 마이페이지 알림 섹션에 "시그널" 관련 UI 없음
- [ ] `./gradlew check` + `make web-check` BUILD SUCCESSFUL
- [ ] Flyway V11 migration 적용 후 `on_signal_change` 컬럼 부재 확인
- [ ] PR 생성 + squash merge 완료

## 7. Implementation Order

1. Plan + Design 문서 작성
2. FE UI 개선 (A): `bookmark-button`, `notification-button` 텍스트 제거 + 활성 상태 색
3. FE 시그널 제거 (C): modal · settings · section · types
4. BE 시그널 제거 (C): Entity · Request · Response · Service · EntityTest
5. Flyway V11 migration 추가
6. `make web-check` + `./gradlew check`
7. 커밋 → push → PR → merge → archive

---

## Version History

| Version | Date | Changes | Author |
|---|---|---|---|
| 1.0 | 2026-04-20 | 초기 Plan — A+C 범위 확정 | wonseok-han |
