# header-toolbox Planning Document

> **Summary**: 헤더 간소화 + 플로팅 툴박스 + 스낵바 토스트 + 마이페이지 UI 개선. (A) 헤더에는 검색+유저 아이콘만 남기고, 유틸리티(테마·로그아웃·피드백)는 플로팅 패널로 분리. (B) 북마크 추가/해제·알림 설정/삭제 시 스낵바 토스트로 사용자 피드백 제공. (C) 마이페이지 전면 리디자인 — 카드 기반 레이아웃, 프로필·북마크·알림·계정 섹션 시각 개선
>
> **Project**: 지금이니?! (Nowini)
> **Version**: v0.1.0-beta
> **Author**: wonseok-han
> **Date**: 2026-04-24
> **Status**: Draft

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | 헤더에 버튼 누적(검색·테마·인증)으로 복잡. 북마크/알림 액션에 피드백 없음. 마이페이지 UI가 투박하고 정보 밀도 낮음 |
| **Solution** | (A) 헤더→검색+유저아이콘, 유틸리티→플로팅 툴박스. (B) 스낵바 토스트로 액션 피드백. (C) 마이페이지 카드 기반 리디자인 |
| **Function/UX Effect** | 헤더 간소화로 확장성 확보. 북마크/알림 즉시 피드백으로 사용성 향상. 마이페이지 시각 품질 향상 |
| **Core Value** | 헤더 복잡도 고정 + 유틸리티 확장성 + 액션 피드백 + UI 품질 개선 |

---

## 1. Overview

### 1.1 Purpose

3가지 UX 개선을 하나의 작업으로 묶어 진행합니다:

1. **헤더 간소화 + 플로팅 툴박스** — 헤더를 로고+검색+유저 아이콘으로 단순화하고, 유틸리티(테마·로그아웃·피드백)를 우하단 플로팅 버튼으로 분리. 기능 추가 시 헤더 변경 없이 툴박스에 항목만 추가
2. **스낵바 토스트** — 북마크 추가/해제, 알림 설정/삭제 등 사용자 액션에 대한 즉시 피드백 부재 해결
3. **마이페이지 UI 리디자인** — 현재 투박한 레이아웃을 카드 기반으로 전면 개선

### 1.2 Background

**현재 헤더 구성:**
```
[Logo] [Beta]                    [돋보기] [테마토글] [로그인/로그아웃+마이페이지]
```

**문제:**
- 기능 추가 시마다 헤더 우측이 복잡해짐 (현재 이미 4~5개 요소)
- 모바일에서 공간 부족 — 로그아웃/마이페이지 텍스트가 잘림
- 테마 토글 스위치가 헤더에 있으면 시각적 노이즈 (자주 쓰지 않는 기능)

**목표 헤더:**
```
[Logo] [Beta]                    [검색 input + 돋보기]
```

### 1.3 Related Documents

- 헤더: `apps/web/src/components/layout/site-header.tsx`
- 테마: `apps/web/src/components/theme/theme-switcher.tsx`
- 인증 메뉴: `apps/web/src/features/auth/user-menu.tsx`
- 검색 모달: `apps/web/src/features/search/search-modal.tsx`

---

## 2. Scope

### 2.1 In Scope

**A. 헤더 간소화**
- [ ] 헤더에서 ThemeSwitcher 제거
- [ ] UserMenu → 유저 아이콘(아바타)으로 축소 (인증 시 → 마이페이지 링크, 비인증 시 → 로그인 링크)
- [ ] 검색 입력창을 헤더 우측에 인라인 배치 (모바일: 돋보기 아이콘만, 데스크톱: input + 돋보기)
- [ ] `⌘K` 단축키는 검색 모달 열기 유지

**B. 플로팅 툴박스 버튼 (신규)**
- [ ] 화면 우하단 고정 플로팅 버튼 (FAB)
- [ ] 클릭 시 패널(팝오버) 열림/닫힘
- [ ] 패널 항목: 테마 전환 · 피드백 보내기 · 로그아웃 (인증 시)
- [ ] 패널 외부 클릭 시 닫힘
- [ ] 스크롤 시 약간 투명해지는 등 방해 최소화

**C. 스낵바 토스트 (신규)**
- [ ] 공용 Snackbar/Toast 컴포넌트 생성 (하단 중앙, 자동 dismiss)
- [ ] 북마크 추가 시 "북마크에 추가했습니다" 스낵바
- [ ] 북마크 해제 시 "북마크를 해제했습니다" 스낵바
- [ ] 알림 설정 저장 시 "알림이 설정되었습니다" 스낵바
- [ ] 알림 삭제 시 "알림이 해제되었습니다" 스낵바
- [ ] 마이페이지 북마크 카드 X 버튼 해제 시에도 스낵바

**D. 마이페이지 UI 리디자인**
- [ ] 프로필 섹션 — 더 넓은 카드, 아바타 강조, 가입일·이메일 레이아웃 개선
- [ ] 북마크 섹션 — 카드 그리드 시각 개선, 가격/등락률 표시 강화
- [ ] 알림 섹션 — 카드 스타일 통일, 상태 배지 가독성 개선
- [ ] 계정 섹션 — 위험 액션(탈퇴) 명확한 시각 분리
- [ ] 전체 레이아웃 — 섹션 간격·타이포그래피·빈 상태 일관성
- [ ] 로그아웃 버튼은 프로필 섹션에서 제거 (플로팅 툴박스로 이동)

### 2.2 Out of Scope

- BE API 추가/변경 (없음)
- 검색 모달 기능 변경 (기존 SearchModal 그대로 유지)
- 최근 본 종목 기능 (별도 feature)
- 플로팅 버튼 드래그 위치 변경
- 알림 기능 통합 (향후 확장 가능)
- 스낵바 undo 기능 (v2 고려)
- 마이페이지 새 섹션 추가 (기존 4개 섹션 리디자인만)

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | 헤더에서 ThemeSwitcher 제거 | High | Pending |
| FR-02 | 헤더 UserMenu → 유저 아이콘 축소 (인증: 아이콘→마이페이지, 비인증: 로그인 링크) | High | Pending |
| FR-03 | 헤더 우측에 검색 입력창 배치 (데스크톱: input, 모바일: 아이콘) | High | Pending |
| FR-04 | 화면 우하단 플로팅 버튼 (FAB) 상시 노출 | High | Pending |
| FR-05 | FAB 클릭 시 툴박스 패널 열림/닫힘 토글 | High | Pending |
| FR-06 | 패널 항목: 테마 전환 (라이트/다크 토글) | High | Pending |
| FR-07 | 패널 항목: 피드백 보내기 링크 | Medium | Pending |
| FR-08 | 패널 항목: 로그아웃 (인증 시에만 표시) | High | Pending |
| FR-09 | 패널 외부 클릭 시 자동 닫힘 | High | Pending |
| FR-10 | ESC 키로 패널 닫힘 | Medium | Pending |
| FR-11 | ⌘K 검색 단축키 기존대로 동작 | High | Pending |
| FR-12 | 공용 Snackbar 컴포넌트 (하단 중앙, 3초 자동 dismiss, 수동 닫기) | High | Pending |
| FR-13 | 북마크 추가 시 "북마크에 추가했습니다" 스낵바 표시 | High | Pending |
| FR-14 | 북마크 해제 시 "북마크를 해제했습니다" 스낵바 표시 | High | Pending |
| FR-15 | 알림 설정 저장 시 "알림이 설정되었습니다" 스낵바 표시 | High | Pending |
| FR-16 | 알림 삭제 시 "알림이 해제되었습니다" 스낵바 표시 | High | Pending |
| FR-17 | 마이페이지 프로필 섹션 리디자인 (로그아웃 버튼 제거) | High | Pending |
| FR-18 | 마이페이지 북마크 섹션 카드 UI 개선 | High | Pending |
| FR-19 | 마이페이지 알림 섹션 카드 스타일 통일 | High | Pending |
| FR-20 | 마이페이지 계정 섹션 시각 분리 강화 | Medium | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| 접근성 | FAB/패널 키보드 접근 가능 (focus trap 불필요, Tab 순서 자연) | 수동 검증 |
| 반응성 | 3테마 정상 렌더링, 모바일/데스크톱 공통 동작 | 수동 검증 |
| 성능 | 패널 열기/닫기 60fps 애니메이션 | 체감 확인 |
| z-index | FAB > 일반 콘텐츠, < 검색 모달 | z-index 계층 확인 |

---

## 4. Detailed Design Decisions

### 4.1 헤더 변경 (Before → After)

**Before:**
```
[Logo] [Beta]          [🔍버튼] [🌙/☀️토글] [로그인] or [마이페이지] [로그아웃]
```

**After (데스크톱):**
```
[Logo] [Beta]                    [🔍 종목 검색...  ⌘K]  [👤]
```

**After (모바일):**
```
[Logo] [Beta]                                    [🔍]  [👤]
```

- 데스크톱: 축약 input (`placeholder="종목 검색..."`, 우측에 `⌘K` 배지). 클릭 또는 `⌘K` → 기존 SearchModal 오픈 (input은 트리거 역할)
- 모바일: 돋보기 아이콘 버튼만 (기존과 동일)
- 유저 아이콘: 인증 시 → 원형 아바타(이니셜), 클릭 시 `/my` 이동. 비인증 시 → "로그인" 텍스트 링크

### 4.2 플로팅 툴박스 레이아웃

```
                                          ┌──────────────────────┐
                                          │  테마                │
                                          │  ☀️ 라이트  🌙 다크   │
                                          ├──────────────────────┤
                                          │  💬 피드백 보내기    │
                                          ├──────────────────────┤
                                          │  🚪 로그아웃         │ ← 인증 시에만
                                          └──────────────────────┘
                                                      [⚙️]  ← FAB (우하단 고정)
```

- FAB 아이콘: 톱니바퀴(⚙️) 또는 도구(🔧) — 구현 시 결정
- 패널: FAB 위로 펼쳐지는 팝오버 (아래→위 방향)
- 열기/닫기: scale + opacity 트랜지션 (200ms)
- 비인증 시: "로그아웃" 숨김

### 4.3 패널 항목 구조

```typescript
// 정적 항목
const TOOL_ITEMS = {
  theme: { type: 'theme-toggle' },    // 라이트/다크 세그먼트 버튼
  myPage: { type: 'link', href: '/my', label: '마이페이지', authOnly: true },
  feedback: { type: 'link', href: '/feedback', label: '피드백 보내기' },
  auth: { type: 'auth-action' },       // 로그인 or 로그아웃
};
```

### 4.4 z-index 계층

| 요소 | z-index | 비고 |
|------|---------|------|
| 헤더 (sticky) | z-40 | 기존 유지 |
| FAB | z-30 | 헤더 아래, 콘텐츠 위 |
| 툴박스 패널 | z-35 | FAB 위, 헤더 아래 |
| 검색 모달 | z-50 | 모든 것 위 |

### 4.5 스크롤 시 FAB 동작

- 기본: 불투명 (opacity-100)
- 스크롤 중: 반투명 (opacity-60) — 콘텐츠 방해 최소화
- 호버 시: 불투명 복귀 (opacity-100)

### 4.6 스낵바 토스트

```
┌──────────────────────────────────────────────────────┐
│    ★ 북마크에 추가했습니다                    ✕     │
└──────────────────────────────────────────────────────┘
               ↑ 하단 중앙, bottom-6
```

- **위치**: 하단 중앙 (`fixed bottom-6 left-1/2 -translate-x-1/2`)
- **동작**: 슬라이드업 + 페이드인 (200ms), 3초 후 자동 dismiss, X 수동 닫기
- **스택**: 여러 스낵바 동시 표시 불필요 — 새 스낵바가 이전 것을 대체
- **z-index**: z-50 (검색 모달과 동급 — 모든 것 위에 표시)
- **상태 관리**: Zustand store (`useSnackbarStore`) — `show(message)` / `hide()`
- **사용처**: BookmarkButton(추가/해제), NotificationSettingModal(저장), NotificationSection(삭제), BookmarkCard(해제)

```typescript
// 사용 예시
const showSnackbar = useSnackbarStore((s) => s.show);
addMutation.mutate(ticker, {
  onSuccess: () => showSnackbar('북마크에 추가했습니다'),
});
```

### 4.7 마이페이지 UI 리디자인

**Before (현재):**
- 단순 border 카드, 좁은 프로필 영역
- 북마크 그리드가 밋밋한 border-only 카드
- 알림 리스트가 보더만 있는 행
- 로그아웃 버튼이 프로필 섹션에 중복 (툴박스와 겹침)

**After:**

**프로필 섹션:**
- 큰 아바타 원(16→20 크기) + 이메일 + 가입일
- 로그아웃 버튼 제거 (플로팅 툴박스로 이동)
- 배경 그라데이션 또는 브랜드 컬러 액센트

**북마크 섹션:**
- 2열 그리드 유지, 카드 내부 개선: 좌측 티커+이름, 우측 가격+등락률 정렬
- hover 시 섀도우 강화, 삭제 버튼 hover에만 표시 (모바일은 항상)
- 빈 상태: 일러스트 + "종목 상세에서 ☆ 버튼으로 북마크하세요" CTA

**알림 섹션:**
- 카드 스타일 (border-only → 배경색 있는 카드)로 통일
- 활성/비활성 상태를 색상 배지로 명확히 구분
- 편집/삭제 버튼 아이콘 크기 증가 + 호버 피드백 강화

**계정 섹션:**
- 별도 카드로 분리, 경고 색상(red border) + 아이콘
- "회원 탈퇴" 텍스트 + 설명문 추가 ("탈퇴 시 모든 데이터가 삭제됩니다")

---

## 5. Success Criteria

### 5.1 Definition of Done

- [ ] 헤더에 검색+유저아이콘만 남아있음 (ThemeSwitcher, UserMenu 제거)
- [ ] 플로팅 FAB 버튼이 우하단에 항상 표시
- [ ] FAB 클릭 → 패널 열림 → 테마 전환/로그아웃/피드백 동작
- [ ] 패널 외부 클릭/ESC로 닫힘
- [ ] `⌘K` 검색 기존대로 동작
- [ ] 북마크 추가/해제 시 스낵바 표시
- [ ] 알림 설정/삭제 시 스낵바 표시
- [ ] 마이페이지 4개 섹션 전면 리디자인 완료
- [ ] 마이페이지 프로필에서 로그아웃 버튼 제거
- [ ] `make web-check` 통과

### 5.2 Quality Criteria

- [ ] 3테마 정상 렌더링 (헤더, FAB, 스낵바, 마이페이지 모두)
- [ ] 모바일/데스크톱 공통 동작
- [ ] 열기/닫기·스낵바 애니메이션 자연스러움
- [ ] 마이페이지 빈 상태(북마크 0건, 알림 0건) 정상 표시

---

## 6. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| FAB가 콘텐츠 가림 (특히 모바일) | Medium | Medium | right-4 bottom-4 위치 + 스크롤 시 반투명 + 작은 사이즈(48px) |
| 플로팅 버튼 용도를 사용자가 모를 수 있음 | Low | Medium | 톱니바퀴 아이콘은 설정 의미가 직관적. 필요 시 첫 방문 툴팁 추가 |
| 검색 input이 헤더에 들어가면 모바일에서 너무 넓을 수 있음 | Low | Low | 모바일은 아이콘만, sm: 이상에서만 input 표시 |
| 스낵바가 FAB과 겹칠 수 있음 | Low | Medium | 스낵바는 하단 중앙, FAB은 하단 우측 — 겹침 최소. 필요 시 스낵바 위치 조정 |
| 마이페이지 리디자인으로 기능 누락 가능 | Medium | Low | 기존 기능 목록 체크리스트로 검증 (북마크 CRUD, 알림 CRUD, 탈퇴) |

---

## 7. Architecture Considerations

### 7.1 변경 범위 (FE Only)

| 파일 | 변경 |
|------|------|
| `site-header.tsx` | ThemeSwitcher 제거, UserMenu → 유저 아이콘 축소, 검색 input 인라인 배치 |
| `user-menu.tsx` | 유저 아이콘(아바타) + 마이페이지 링크 / 로그인 링크로 축소 |
| 신규: `floating-toolbox.tsx` | FAB + 패널 컴포넌트 |
| 신규: `snackbar.tsx` | 공용 스낵바 컴포넌트 |
| 신규: `use-snackbar-store.ts` | Zustand 기반 스낵바 상태 관리 |
| `layout.tsx` | FloatingToolbox + Snackbar 추가 |
| `theme-switcher.tsx` | 기존 유지 (툴박스 패널 내부에서 재사용) |
| `bookmark-button.tsx` | mutation onSuccess에 스낵바 호출 추가 |
| `bookmark-card.tsx` | 해제 mutation onSuccess에 스낵바 호출 추가 |
| `notification-button.tsx` / `notification-setting-modal.tsx` | 설정 저장 시 스낵바 호출 추가 |
| `notification-section.tsx` | 삭제 mutation onSuccess에 스낵바 호출 추가 |
| `my/page.tsx` | 레이아웃·타이포그래피 개선 |
| `profile-section.tsx` | 리디자인 + 로그아웃 버튼 제거 |
| `bookmark-grid.tsx` | 그리드·빈 상태 개선 |
| `bookmark-card.tsx` | 카드 UI 개선 |
| `notification-section.tsx` | 카드 스타일 통일·상태 배지 개선 |
| `account-section.tsx` | 위험 액션 시각 분리 |

**BE 변경: 없음**

---

## 8. Next Steps

1. [ ] Write design document (`header-toolbox.design.md`)
2. [ ] Start implementation

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-24 | Initial draft (Command Palette 방향) | wonseok-han |
| 0.2 | 2026-04-24 | 플로팅 툴박스 방향으로 전면 재작성 | wonseok-han |
| 0.3 | 2026-04-24 | 스낵바 토스트 + 마이페이지 UI 리디자인 스코프 추가 | wonseok-han |
