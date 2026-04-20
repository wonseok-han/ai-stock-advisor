---
template: analysis
version: 1.2
feature: notification-dedup
date: 2026-04-20
author: wonseok-han (via bkit:gap-detector)
project: AI Stock Advisor
status: Complete
---

# notification-dedup Gap Analysis

> **Summary**: Design vs 실제 구현 비교. Match Rate **100%**, 갭 없음.
>
> **Design Doc**: [notification-dedup.design.md](../02-design/features/notification-dedup.design.md)
> **Plan Doc**: [notification-dedup.plan.md](../01-plan/features/notification-dedup.plan.md)
> **Implementation Commit**: `54c3eae` on `feat/notification-dedup`
> **Date**: 2026-04-20

---

## Executive Summary

| 항목 | 값 |
|---|---|
| **Match Rate** | **100%** |
| **Missing items** | 0 |
| **Added items** | 0 (2개 bonus test는 커버리지 확장) |
| **Changed items** | 0 |
| **Non-gap notes** | 5 (의도된 조정 사항, 갭 아님) |
| **Test count** | 14 (Policy 9 + Entity 5) |
| **Recommendation** | Proceed to `/pdca report notification-dedup` |

---

## 1. FR Coverage (Plan §3.1)

| FR | Requirement | Evidence | Status |
|----|-------------|----------|:------:|
| FR-01 | 임계값 최초 돌파 시에만 발송 (false→true 전이) | `NotificationDedupPolicy.java:44-51` (`aboveTrigger && !lastTriggeredAbove` 게이트) | ✅ |
| FR-02 | `|change|` 가 threshold × 0.6 아래로 내려가면 `lastTriggeredAbove` 리셋 | `NotificationDedupPolicy.java:58-61` + `NotificationSettingEntity.resetTrigger():87-89` | ✅ |
| FR-03 | 리셋 후 재돌파해도 쿨다운(4h) 내면 발송 보류 | `NotificationDedupPolicy.java:45-49` (SEND 전에 쿨다운 체크) | ✅ |
| FR-04 | 임계값 변경 시 상태 2개 필드 모두 리셋 | `NotificationSettingEntity.update():68-78` → `NotificationSettingService.upsert():34` 경로로 자동 연결 | ✅ |
| FR-05 | `app.notification.dedup.*` 로 설정 주입 | `application.yml:93-96` + `NotificationDedupProperties.java:12` (prefix 일치) | ✅ |
| FR-06 | Skipped 사유 디버그 로그 | `NotificationCheckService.java:111-112, 118-119, 122-126` (모든 분기 로깅) | ✅ |

## 2. T1~T8 Test Coverage (Design §8.2)

| Test | Scenario | Location | Status |
|------|----------|----------|:------:|
| T1 | 미돌파 & 이전 상태 없음 → NOOP | `NotificationDedupPolicyTest.java:24-30` | ✅ |
| T2 | 첫 돌파 (5%=5%) → SEND | line 33-39 | ✅ |
| T3 | 초과 유지 (5.3%, notified 30min ago) → SKIP_NO_TRANSITION | line 42-48 | ✅ |
| T4 | 경계 진동 (4.9%, reset=3 ~ trigger=5 사이) → NOOP | line 51-57 | ✅ |
| T5 | 리셋 아래 (2.5%) → RESET_ONLY | line 60-66 | ✅ |
| T6 | 재돌파 but 쿨다운 중 (5.1%, 1h ago) → SKIP_COOLDOWN | line 69-75 | ✅ |
| T7 | 재돌파 & 쿨다운 만료 (5.1%, 5h ago) → SEND | line 78-84 | ✅ |
| T8 | 시계 역전 (lastNotifiedAt 이 미래) → SKIP_COOLDOWN fail-safe | line 87-93 | ✅ |
| **T9** | **(Bonus)** 정확히 리셋 임계값(3%) → NOOP (엄격 `<` 검증) | line 96-102 | ✅ |
| U1 | 임계값 변경 시 상태 리셋 | `NotificationSettingEntityTest.java:26-36` | ✅ |
| U2 | 동일 임계값 재저장 시 상태 유지 | line 39-49 | ✅ |
| U3 | enabled 토글 시 상태 유지 | line 52-61 | ✅ |
| **U4** | **(Bonus)** `resetTrigger()` 는 lastNotifiedAt 을 건드리지 않음 (쿨다운 유지 검증) | line 64-72 | ✅ |
| **U5** | **(Bonus)** `markNotified()` 가 타임스탬프 + boolean 모두 설정 | line 75-85 | ✅ |

Design 대비 T1~T8 모두 구현. 추가로 T9/U4/U5 로 boundary 조건 보강.

## 3. Design §4 (Core Logic) 항목별 대조

| Design Item | Implementation | Status |
|---|---|:---:|
| §4.1 State diagram — Action 5종(SEND/SKIP_NO_TRANSITION/SKIP_COOLDOWN/RESET_ONLY/NOOP) | `NotificationDedupPolicy.Action` 동일 (line 27) | ✅ |
| §4.2 `decide()` 시그니처 (7 params, Decision record) | line 31-39 동일 | ✅ |
| §4.2 불변식 "SEND only if aboveTrigger ∧ ¬lastTriggeredAbove" | line 44 강제 | ✅ |
| §4.2 RESET_ONLY 는 lastNotifiedAt 불변 | `Entity.resetTrigger():87-89` boolean 만 토글, U4 로 검증 | ✅ |
| §4.3 Properties record + defaults (0.6, PT4H) | `NotificationDedupProperties.java:17-20` 동일 | ✅ |
| §4.3 `app.notification.dedup.*` namespace | `application.yml:93-96` | ✅ |
| §4.4 `PushService.sendToUser` void → boolean | `PushService.java:77` 시그니처 변경, line 102 `return success > 0` | ✅ |
| §4.4 `webPushService == null` / subs 비어있음 → false | line 78-85 | ✅ |
| §4.4 per-endpoint try/catch, partial success = true | line 92-102 Design 예시와 동일 | ✅ |
| Design §2.1 flow: SEND 성공 시만 markNotified | `NotificationCheckService.java:107-113` (if-else on `sent`) | ✅ |
| Design §11.2 구현 순서 (Policy→Properties→V*→Entity→Tests→PushService→CheckService) | 커밋 `54c3eae` 에 반영 | ✅ |

---

## 4. Gaps

**없음.** FR-01~FR-06 전부, T1~T8 전부, Design §4 전부가 명세대로 구현됨.

---

## 5. Non-Gap Notes (의도된 조정, 갭 아님)

| # | 항목 | Design 기재 | 실제 | 분류 |
|---|---|---|---|:---:|
| N1 | 마이그레이션 버전 | `V8__notification_dedup.sql` | `V10__notification_dedup.sql` | **정당한 조정** — V8/V9 슬롯이 이미 `candles.sql`, `user_account_deletion.sql` 로 점유됨. 파일명 충돌 시 Flyway migrate 실패. SQL 본문은 Design §3.3 과 바이트 단위 동일. Design §11.1 의 "V8" 참조는 stale 하나 무해. |
| N2 | `NotificationCheckServiceIntegrationTest` (I1/I2/I3) | §8.2 / §11.1 에 `[NEW, optional]` 로 명시 | 미작성 | **의도된 연기** — Design 자체에서 optional 로 지정. Policy 는 T1~T9 로 철저히 커버, Entity 는 U1~U5 로 커버. 통합 테스트는 추후 Zero Script QA 또는 Phase 5 과제로 이관. |
| N3 | `NotificationSettingService.upsert` 에서 임계값 변경 감지 wiring | Design §5: "Entity.update 가 내부적으로 처리" | `upsert():34` 가 `entity.update(...)` 호출하여 내부 리셋 — 별도 변경 불필요 | **의도 그대로** — Entity 가 규칙 소유. FR-04 가 U1 로 end-to-end 증명. |
| N4 | `@ConfigurationProperties` scan wiring | Plan §7.1 에서 "확인 필요 — 없으면 도입" | `ApiApplication.java:10` 에 `@ConfigurationPropertiesScan` 이미 존재 | **기존 인프라 재사용** — 신규 boilerplate 불필요. |
| N5 | Plan §3.1 FR-05 의 `notification.dedup.*` 표기 | Design §4.3 에서 `app.notification.dedup.*` 로 정정 | 코드는 `app.notification.dedup.*` 따름 | **Design 의 정정 반영** — Design 이 Plan 의 typo 를 명시적으로 지적하고 코드가 따름. |

---

## 6. Quality Metrics

| 지표 | 값 | 기준 | 결과 |
|---|---|---|:---:|
| Match Rate | 100% | ≥90% | ✅ |
| `./gradlew check` | BUILD SUCCESSFUL | pass | ✅ |
| Unit tests | 14 (9 Policy + 5 Entity) | — | ✅ |
| 빌드 경고 | 0 | 0 | ✅ |
| 커밋 로그 정합성 | docs 커밋 1개 + feat 커밋 1개, PDCA 사이클과 1:1 대응 | clean | ✅ |

---

## 7. Recommendation

- 매칭률 ≥90% 달성 → **`/pdca report notification-dedup`** 로 완료 리포트 생성 권장.
- 반복 개선(`/pdca iterate`) 불필요 (갭 0).
- PR #12 (develop 타깃) 머지 대기 중. 머지 전 FR-05 설정값이 스테이징에서 정상 바인딩되는지 한 번 확인 권장.
- 후속 과제 (이번 feature 범위 외):
  - `NotificationCheckServiceIntegrationTest` 추가 (I1~I3)
  - 뉴스(`onNewNews`) / 신호 변경(`onSignalChange`) 알림 구현 시 동일 dedup 패턴 적용

---

## Version History

| Version | Date | Changes | Author |
|---|---|---|---|
| 1.0 | 2026-04-20 | 초기 분석 — Match Rate 100%, 갭 없음, non-gap notes 5건 | bkit:gap-detector + wonseok-han |
