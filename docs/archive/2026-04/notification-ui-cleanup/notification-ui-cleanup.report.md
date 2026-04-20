---
template: report
version: 1.0
feature: notification-ui-cleanup
date: 2026-04-20
author: wonseok-han
project: AI Stock Advisor
status: Completed
---

# notification-ui-cleanup Completion Report

> **Summary**: 종목 상세 페이지 북마크/알림 버튼을 아이콘 전용으로 정리하고, 알림 활성 상태를 색으로 피드백. 죽은 토글 `onSignalChange`를 FE·BE·DB 전 계층에서 제거. Match Rate **100%**.
>
> **Project**: AI Stock Advisor
> **Feature**: notification-ui-cleanup (Phase 4.5.2)
> **Branch**: feat/notification-ui-cleanup
> **PR**: [#13](https://github.com/wonseok-han/ai-stock-advisor/pull/13) (base: develop)
> **Completion Date**: 2026-04-20

---

## Executive Summary

| 관점 | 내용 |
|---|---|
| **Problem** | 북마크/알림 버튼 옆 텍스트(`북마크됨`/`알림`) 가 시각적 잡음이었고, 알림 설정 후에도 북마크와 달리 "눌렀는지" 보이지 않아 재클릭/혼란 유발. 모달의 `AI 시그널 변화 시` 토글은 DB·DTO·UI는 있지만 발송 로직이 없는 **죽은 토글**이었고, 구현하려면 15분 배치 LLM 호출(600+/day) 로 토큰 비용이 "참고용 분석 도구" 포지셔닝 대비 과함. |
| **Solution** | (A) 아이콘 전용으로 축소(`★`/`☆`, bell SVG), 알림 버튼은 `useNotificationSettings()` + `useMemo` 로 현재 티커 활성 여부를 계산해 blue 계열 색 분기. (C) `onSignalChange` 를 types → Request/Response → Entity → Service → EntityTest → DB 까지 풀 스택에서 완전 삭제. Flyway V11 로 `DROP COLUMN IF EXISTS` 추가. `onNewNews` 는 추후 `notification-news` feature 를 위해 유지. |
| **Function UX Effect** | 버튼이 깔끔해지고 알림 설정 여부를 한눈에 구분 가능. 죽은 옵션이 사라져 사용자는 실제 동작하는 옵션만 보게 됨. 북마크(노랑) vs 알림(파랑) 색 구분으로 기능 혼동 제거. `title` 속성 추가로 hover tooltip 도 제공. |
| **Core Value** | "참고용 분석 도구" 포지셔닝에 맞는 최소 UI. YAGNI 적용으로 기능 부채 제거 — 향후 AI 시그널 알림이 정말 필요해지면 순수 함수 `NotificationDedupPolicy` 패턴 재사용해 재도입 가능. |

---

## 1. Project Overview

| 항목 | 값 |
|---|---|
| **Feature** | notification-ui-cleanup |
| **Type** | UI Polish + Dead Feature Removal (Phase 4.5 후속 #2) |
| **Branch** | feat/notification-ui-cleanup |
| **PR** | [#13](https://github.com/wonseok-han/ai-stock-advisor/pull/13) |
| **Target** | develop (base branch) |
| **Start Date** | 2026-04-20 |
| **Completion Date** | 2026-04-20 |
| **Duration** | Single day (plan+design+do+check all in one cycle) |
| **Owner** | wonseok-han |

---

## 2. PDCA Cycle Summary

### Plan Phase
- **Document**: [notification-ui-cleanup.plan.md](../../01-plan/features/notification-ui-cleanup.plan.md)
- **Goal**: (A) UI polish + (C) 죽은 토글 제거. (B) 뉴스 알림 실제 발송은 별도 feature 로 분리.
- **Key Decisions**:
  - 옵션 2 (A+C 동시, B 분리) 채택 — AI 시그널 알림 구현 시 토큰 비용이 가치 대비 과함을 확인하고 **삭제** 선택
  - `onNewNews` 토글은 유지 — 추후 `notification-news` feature 에서 실제 구현 예정
  - Flyway V11 로 `DROP COLUMN IF EXISTS` (V7 원본 CREATE 는 불변, 히스토리 보존)

### Design Phase
- **Document**: [notification-ui-cleanup.design.md](../../02-design/features/notification-ui-cleanup.design.md)
- **Key Designs**:
  - **Axis A (UI Polish, FE-only)**:
    - BookmarkButton: 텍스트 제거, `px-2 py-1.5` 정사각형, `★`/`☆` 단일 글리프
    - NotificationButton: 종 SVG 만, `useNotificationSettings()` 훅으로 `isActive` 계산, blue 색 분기, 활성 시 `fill="currentColor"`
    - `aria-label` 상태별 분기로 접근성 유지, `title` 속성으로 hover tooltip 제공
  - **Axis C (onSignalChange 제거, Full-stack)**:
    - FE: `types/notification.ts` 필드 삭제 → modal/settings/section UI 제거 → `handleToggle` field 유니언 축소
    - BE: Entity 필드+getter 제거 → `update()` 시그니처 4-arg → 3-arg → Request/Response record 정리 → Service 호출부 정렬
    - DB: V11 `DROP COLUMN IF EXISTS on_signal_change`
    - Test: U1~U5 모두 3-arg 로 갱신, `isOnSignalChange()` assertion 삭제
  - **Invariant 보장**: `NotificationCheckService`, `NotificationDedupPolicy`, `PushService` 불변, `onNewNews` 전 계층 유지

### Do Phase
- **Implementation Commit**: `bcb1507` "feat(notification): icon-only buttons + active state + drop dead signal toggle"
- **Files Changed**: 12 files, +40 / -48 (net -8 lines)
- **Key Implementations**:
  1. `V11__drop_on_signal_change.sql` (new, 6 lines) — `DROP COLUMN IF EXISTS on_signal_change`
  2. `NotificationSettingEntity.java` — 필드·getter 제거, `update()` 3-arg 로 축소
  3. `NotificationSettingRequest.java` / `NotificationSettingResponse.java` — record 3 필드로 축소
  4. `NotificationSettingService.java` — `update()` 호출 인자 조정, `toResponse()` 정리
  5. `NotificationSettingEntityTest.java` — U1~U5 3-arg 반영, `isOnSignalChange` assertion 삭제
  6. `bookmark-button.tsx` — 텍스트 제거, 아이콘 전용 재작성, `title` 추가
  7. `notification-button.tsx` — `useNotificationSettings()` + `useMemo` 로 `isActive`, blue 활성 색, `fill="currentColor"`, `title` 추가
  8. `notification-setting-modal.tsx` — `initialOnSignalChange` prop, state, ToggleRow 제거
  9. `notification-settings.tsx` — field 유니언 축소(`'onNewNews' | 'enabled'`), 시그널 ToggleChip 삭제
  10. `my-page/notification-section.tsx` — "시그널" 배지 삭제
  11. `types/notification.ts` — `onSignalChange` 필드 제거

### Check Phase
- **Analysis Document**: [notification-ui-cleanup.analysis.md](../../03-analysis/notification-ui-cleanup.analysis.md)
- **Match Rate**: **100%**
- **Gap Count**: 0 (Missing 0, Changed 0)
- **Added**: 1 non-blocking — `title` 속성 (`aria-label` 동일 값, hover tooltip UX 개선)
- **Build Status**: `./gradlew check` BUILD SUCCESSFUL, `tsc --noEmit` ✅, `pnpm lint` 0 errors
- **Residual Reference**: `apps/` 내 `onSignalChange` / `isOnSignalChange` / `signalChange` 전부 0건, `on_signal_change` 2건(V7 원본 + V11 DROP, Flyway 히스토리 기대값)
- **Invariants Preserved**: `NotificationCheckService`, `NotificationDedupPolicy`, `PushService` 불변 (grep 0건), `onNewNews` 전 계층 18건 참조 유지

---

## 3. Completed Items

### 3.1 Functional Requirements (FR-01 ~ FR-08) — All Met

| FR | Requirement | Evidence |
|----|-------------|----------|
| FR-01 | 북마크 버튼 아이콘 전용 + `aria-label` | `bookmark-button.tsx:33-45` (`★`/`☆` 단일, aria-label 분기) |
| FR-02 | 알림 버튼 아이콘 전용 | `notification-button.tsx:31-54` (종 SVG 만) |
| FR-03 | 활성 상태 색 분기 (blue) | `notification-button.tsx:12-19, 33-37, 43` (`useNotificationSettings()` + `useMemo` + `fill="currentColor"`) |
| FR-04 | 모달 시그널 토글 제거 | `notification-setting-modal.tsx:95-99` (ToggleRow 1개만, 뉴스 토글만) |
| FR-05 | 마이페이지/리스트 시그널 UI 제거 | `my-page/notification-section.tsx`, `notification/notification-settings.tsx` 시그널 블록 부재 |
| FR-06 | BE DTO에서 `onSignalChange` 필드 제거 | `Request.java`, `Response.java`, `Entity.java` 3 필드만 |
| FR-07 | `on_signal_change` 컬럼 삭제 | `V11__drop_on_signal_change.sql` (`DROP COLUMN IF EXISTS`) |
| FR-08 | 기존 데이터 보존 | V11 범위는 단일 컬럼만 — `price_change_threshold`, `on_new_news`, `enabled`, `last_notified_at`, `last_triggered_above` 영향 없음 |

### 3.2 Design Components — All Implemented

**Axis A (UI Polish)**
- ✅ BookmarkButton `★`/`☆` + `px-2 py-1.5 text-base leading-none`
- ✅ NotificationButton 훅 기반 활성 상태 계산 (`useMemo`)
- ✅ 활성 시 종 `fill="currentColor"`, blue 색 (`bg-blue-100 text-blue-700`)
- ✅ `aria-label` 상태별 분기 (`isActive ? '알림 설정됨 (편집)' : '알림 설정'`)
- ✅ 북마크(노랑) vs 알림(파랑) 색 구분

**Axis C (onSignalChange 제거)**
- ✅ FE types 3필드 (`ticker`, `priceChangeThreshold`, `onNewNews`, `enabled`)
- ✅ BE Entity 3 필드 + `update(priceChangeThreshold, onNewNews, enabled)` 3-arg
- ✅ Request/Response record 3 필드 완전 일치
- ✅ Service `update()` + `toResponse()` 갱신
- ✅ EntityTest U1~U5 3-arg 시그니처
- ✅ Flyway V11 `DROP COLUMN IF EXISTS`
- ✅ 모달 ToggleRow 1개로 축소, state/prop 제거
- ✅ Settings field 유니언 `'onNewNews' | 'enabled'` 로 축소

### 3.3 Test Coverage

**기존 테스트 회귀 없음 — 관련 테스트 Green 유지:**
- Entity U1~U5 (5개) — `update()` 3-arg 로 전환 후 all green
- `NotificationDedupPolicyTest` T1~T9 (9개) — 불변, all green
- FE typecheck + lint — 신규 에러 0

### 3.4 Build & Quality Metrics

| 지표 | 결과 |
|------|------|
| `./gradlew check` | BUILD SUCCESSFUL ✅ (37s) |
| FE typecheck (`tsc --noEmit`) | pass ✅ |
| FE lint (`pnpm lint`) | 0 errors ✅ (기존 sw.js warning 1건은 무관) |
| 컴파일 경고 | 0 ✅ |
| Code style | CLAUDE.md 준수 (kebab-case 파일명, 존댓말 등) ✅ |

---

## 4. Deferred / Out-of-Scope Items

### 4.1 Intentional Deferral (별도 feature 로 분리)

- **`notification-news` (뉴스 알림 실제 발송)**
  - `onNewNews` 토글과 DTO/Entity 필드는 이번에 **유지** (미래 구현 위해)
  - `NotificationCheckService.check()` 에 `checkNewNews()` 추가 + `NotificationDedupPolicy` 재사용 패턴으로 구현 예정
  - 토큰 비용 통제를 위해 RSS/뉴스 API 기반(LLM 없이) 으로 설계할 것

### 4.2 YAGNI 로 완전 삭제 (재도입 시 신규 feature 로)

- **AI 시그널 변화 알림 (`onSignalChange`)**
  - Reason: 15분 배치 + LLM 호출(~600+/day/user) 토큰 비용이 "참고용 분석 도구" 포지셔닝 대비 과함
  - 재도입 시: 순수 함수 `NotificationDedupPolicy` 패턴 재사용, 신규 feature 이름 `notification-signal` 권장

### 4.3 Planning 문서 정리 (후속)

- `docs/planning/02-features.md:125`, `03-architecture.md:129`, `06-roadmap.md:123` 에 "시그널 변화" 언급 잔존
- Phase 0 고정본이므로 **수정 신중히** — 향후 시그널 알림 재도입 판단 시점에 일괄 정정

---

## 5. Key Metrics & Facts

| 지표 | 값 |
|---|---|
| **Match Rate** | 100% |
| **Files Changed** | 12 |
| **Lines Added** | +40 |
| **Lines Removed** | -48 |
| **Net Change** | **-8 lines** (순 감소 — 죽은 코드 제거 효과) |
| **Commits** | 1 (bcb1507, squash merge 예정) |
| **Gaps** | 0 |
| **Iterations** | 0 (첫 시도에 100% 달성) |
| **Build Time** | ~37s (./gradlew check) |
| **Breaking Changes** | 0 (public API 유지, DB DROP 은 죽은 컬럼만) |

### 5.1 UX Impact

- **Before**:
  - 버튼: `☆ 북마크` / `🔔 알림` 텍스트 병기 → 시각적 잡음
  - 알림 설정 후 상태 변화 없음 → 재클릭/혼란
  - 모달 2 토글: "새 뉴스 발생 시"(미구현) + "AI 시그널 변화 시"(미구현) → 기대 위배
- **After**:
  - 버튼: `★`/`🔔` 아이콘만, 정사각형
  - 알림 설정 시 blue 활성 색 + 종 채움 → 한눈에 상태 확인
  - 모달 1 토글(뉴스)만 노출 → 죽은 옵션 제거
  - `title` hover tooltip 으로 마우스 유저에게도 맥락 제공

### 5.2 Code Health

- **onSignalChange 참조 (apps/)**: 17 파일 → 0 (완전 제거)
- **NotificationSettingEntity 복잡도**: 4 필드 → 3 필드, `update()` 4-arg → 3-arg
- **DB 스키마**: `notification_settings` 컬럼 1개 감소 (`on_signal_change`)
- **Dead toggle 제거**: 사용자 혼동 유발 UI 1건 제거

---

## 6. Lessons Learned

### 6.1 What Went Well

1. **옵션 분석 단계에서 YAGNI 판단이 정확했음**
   - AI 시그널 알림 구현 전 토큰 비용 견적(~600+ LLM calls/day)을 먼저 산출 → "참고용 분석 도구" 포지셔닝 대비 가치 미달 확인
   - 결과: 구현 대신 **삭제** 선택으로 풀 스택 부채 제거

2. **순수 함수 + 훅 패턴으로 활성 상태 피드백이 간결**
   - `useNotificationSettings()` + `useMemo` 만으로 `isActive` 계산 → 별도 API 호출 없음
   - React Query 캐시를 그대로 재사용하므로 성능 비용 0

3. **Flyway 히스토리 보존 원칙 준수**
   - V7 원본 CREATE 는 불변 유지, V11 로 누적 DROP → 과거 히스토리 재현 가능
   - `DROP COLUMN IF EXISTS` 로 멱등성 확보 (이미 DROP 된 환경 안전)

4. **풀 스택 타입 안전성의 가치**
   - FE `NotificationSetting` 필드 1개 제거 → TS 컴파일러가 영향받는 모든 지점 자동 발견
   - BE record 필드 제거 → 컴파일러가 `update()`, `toResponse()` 호출부 모두 표시

### 6.2 Areas for Improvement

1. **죽은 기능은 조기에 발견해야**
   - `onSignalChange` 는 Phase 4 auth feature 때 DB/DTO 에 함께 추가되었으나, 발송 로직이 빠진 채 방치됨
   - 재발 방지: 신규 토글/옵션 추가 시 "발송/실행 경로 구현" 체크리스트 필수화

2. **`notification-news` 도 같은 리스크**
   - `onNewNews` 도 현재는 죽은 토글 — 이번에 같이 삭제할지 유지할지 선택 필요
   - 결정: 유지 (RSS 기반 구현은 LLM 비용 없어 실현 가능). 단 "언제까지" 미완료 상태를 둘 것인지 로드맵에 기록 필요

3. **Design 문서의 "추가 항목" 표기**
   - `title` 속성이 Design 에 명시되지 않았음 → analysis 에서 "Added 1" 로 분류됨
   - 차후: 접근성 강화는 Design 체크리스트에 기본 포함하는 게 좋음

### 6.3 To Apply Next Time

1. **비용/가치 검토를 Plan 에 공식화**
   - 기능 제안 시 "LLM 호출 빈도/비용 예상" 섹션 템플릿화
   - 토큰 비용 > 가치 → 삭제 or RSS/룰 기반 대안 검토

2. **"Dead Toggle Check" 를 PR 체크리스트에 추가**
   - 새 필드/옵션 추가 시 발송/실행 경로 링크 확인
   - 6개월 이상 dead 상태면 review 대상

3. **UI 활성 상태 피드백 표준화**
   - 북마크(노랑 채움) / 알림(파랑 채움) 패턴을 FE 디자인 시스템에 정립
   - 향후 유사 토글(찜하기, 팔로우 등)에 일관된 색 체계 적용

---

## 7. Follow-up Items & Recommendations

### 7.1 Immediate (PR 머지 후)

1. ✅ PR #13 squash merge → develop
2. ✅ Vercel/Fly.io 배포 시 Flyway V11 자동 적용 확인 (`on_signal_change` 컬럼 부재 검증)
3. ✅ PDCA 문서 `docs/archive/2026-04/notification-ui-cleanup/` 로 아카이브

### 7.2 Short-term (1~2주)

1. **`notification-news` feature 착수 검토**
   - `onNewNews` 가 여전히 dead 토글 상태
   - RSS / 뉴스 API(Finnhub 기존 뉴스 엔드포인트 재사용) 기반 구현
   - `NotificationDedupPolicy` 재사용 — 뉴스 발행 시점 기반 쿨다운 설계

2. **`docs/planning/` 시그널 언급 정리**
   - 02-features.md, 03-architecture.md, 06-roadmap.md 에서 "시그널 변화 알림" 문구 현행화
   - `notification-signal` feature 가 재도입되는 시점에 일괄 정정

### 7.3 Medium-term (1개월+)

1. **FE Button 디자인 시스템 정립**
   - BookmarkButton, NotificationButton 패턴을 공통 `IconToggleButton` 으로 추상화 검토
   - 단 YAGNI 원칙 — 3번째 유사 버튼이 나올 때 추상화

2. **Dead Feature 감사 (Audit)**
   - 전체 DB 컬럼/DTO 필드 중 미사용 경로 주기적 점검
   - 현재 후보: `onNewNews` (deferred), 기타 미확인

---

## 8. Links & References

### 8.1 PDCA Documents

| Phase | Document | Status |
|-------|----------|:------:|
| Plan | [notification-ui-cleanup.plan.md](../../01-plan/features/notification-ui-cleanup.plan.md) | ✅ Complete |
| Design | [notification-ui-cleanup.design.md](../../02-design/features/notification-ui-cleanup.design.md) | ✅ Complete |
| Check | [notification-ui-cleanup.analysis.md](../../03-analysis/notification-ui-cleanup.analysis.md) | ✅ Complete (Match Rate 100%) |
| Report | [notification-ui-cleanup.report.md](./notification-ui-cleanup.report.md) | ✅ This Document |

### 8.2 Implementation Commit

| Commit | Message | Files | Changes |
|--------|---------|-------|---------|
| `bcb1507` | feat(notification): icon-only buttons + active state + drop dead signal toggle | 12 | +40/-48 (net -8) |

### 8.3 Pull Request

- **PR #13**: [feat/notification-ui-cleanup on GitHub](https://github.com/wonseok-han/ai-stock-advisor/pull/13)
- **Base**: develop
- **Target**: Squash merge (1 commit in develop)

### 8.4 Key Code References

| Component | File | Change |
|-----------|------|--------|
| Bookmark icon-only | `apps/web/src/features/bookmark/bookmark-button.tsx` | 재작성 (★/☆) |
| Notification active state | `apps/web/src/features/stock-detail/notification-button.tsx` | useMemo isActive + blue 색 |
| Modal dead toggle 제거 | `apps/web/src/features/stock-detail/notification-setting-modal.tsx` | ToggleRow 1개 |
| Settings field 축소 | `apps/web/src/features/notification/notification-settings.tsx` | 유니언 축소 |
| Section badge 정리 | `apps/web/src/features/my-page/notification-section.tsx` | "시그널" 삭제 |
| FE type 정리 | `apps/web/src/types/notification.ts` | onSignalChange 제거 |
| Entity 3 필드화 | `apps/api/.../infra/NotificationSettingEntity.java` | 필드·getter·update() |
| Request/Response | `apps/api/.../domain/NotificationSetting{Request,Response}.java` | record 3 필드 |
| Service 정리 | `apps/api/.../service/NotificationSettingService.java` | 호출부 2곳 |
| EntityTest 갱신 | `apps/api/.../infra/NotificationSettingEntityTest.java` | U1~U5 3-arg |
| DB Migration | `apps/api/src/main/resources/db/migration/V11__drop_on_signal_change.sql` | DROP COLUMN |

---

## 9. Sign-off

| Role | Name | Date | Status |
|------|------|------|:------:|
| Developer | wonseok-han | 2026-04-20 | ✅ Complete |
| Plan Review | wonseok-han | 2026-04-20 | ✅ Approved |
| Design Review | wonseok-han | 2026-04-20 | ✅ Approved |
| Analysis (Match Rate) | bkit:gap-detector | 2026-04-20 | ✅ 100% |

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2026-04-20 | Initial completion report — Match Rate 100%, 12 files, net -8 lines, 0 gaps, 0 iterations | wonseok-han |
