# header-toolbox Design Document

> **Plan Reference**: `docs/01-plan/features/header-toolbox.plan.md` (v0.3)
>
> **Project**: 지금이니?! (Nowini)
> **Version**: v0.1.0-beta
> **Author**: wonseok-han
> **Date**: 2026-04-24
> **Status**: Draft

---

## 1. Overview

헤더 간소화 + 플로팅 툴박스 + 스낵바 토스트 + 마이페이지 UI 리디자인.

**변경 범위**: FE Only (BE 변경 없음)

---

## 2. Component Architecture

### 2.1 Component Tree (변경 부분)

```
layout.tsx
├── Providers
│   ├── SiteHeader          ← 수정: 검색 input + 유저 아이콘만
│   │   ├── Logo
│   │   ├── SearchTrigger   ← 신규: 데스크톱 input / 모바일 아이콘
│   │   ├── UserAvatar      ← 신규: user-menu.tsx 대체
│   │   └── SearchModal     ← 기존 유지
│   ├── {children}
│   ├── FloatingToolbox     ← 신규: FAB + 패널
│   │   └── ThemeSwitcher   ← 기존 재사용
│   ├── Snackbar            ← 신규: 토스트 알림
│   ├── DisclaimerBanner
│   └── DisclaimerFooter
```

### 2.2 신규 컴포넌트

| 컴포넌트 | 파일 | 역할 |
|----------|------|------|
| `FloatingToolbox` | `components/layout/floating-toolbox.tsx` | FAB 버튼 + 팝오버 패널 (테마/피드백/로그아웃) |
| `Snackbar` | `components/ui/snackbar.tsx` | 하단 중앙 토스트 알림 |
| `useSnackbarStore` | `stores/use-snackbar-store.ts` | Zustand 기반 스낵바 상태 관리 |

### 2.3 수정 컴포넌트

| 컴포넌트 | 파일 | 변경 내용 |
|----------|------|-----------|
| `SiteHeader` | `components/layout/site-header.tsx` | ThemeSwitcher·UserMenu 제거, SearchTrigger+UserAvatar 추가 |
| `UserMenu` | `features/auth/user-menu.tsx` | 아바타 아이콘(인증→`/my`, 비인증→`/auth/login`) 전환 |
| `BookmarkButton` | `features/bookmark/bookmark-button.tsx` | mutation onSuccess 스낵바 |
| `BookmarkCard` | `features/my-page/bookmark-card.tsx` | 해제 스낵바 + UI 리디자인 |
| `BookmarkGrid` | `features/my-page/bookmark-grid.tsx` | 그리드·빈 상태 개선 |
| `NotificationSettingModal` | `features/stock-detail/notification-setting-modal.tsx` | 저장/삭제 스낵바 |
| `NotificationSection` | `features/my-page/notification-section.tsx` | 삭제 스낵바 + 카드 리디자인 |
| `ProfileSection` | `features/my-page/profile-section.tsx` | 리디자인 + 로그아웃 제거 |
| `AccountSection` | `features/my-page/account-section.tsx` | 위험 액션 시각 분리 |
| `MyPage` | `app/my/page.tsx` | 레이아웃·타이포 개선 |
| `RootLayout` | `app/layout.tsx` | FloatingToolbox + Snackbar 추가 |

---

## 3. Detailed Design

### 3.1 SiteHeader 변경

**Before:**
```
[Logo] [Beta]          [🔍버튼] [🌙/☀️토글] [로그인] or [마이페이지] [로그아웃]
```

**After (데스크톱 sm+):**
```
[Logo] [Beta]                    [🔍 종목 검색...  ⌘K]  [👤]
```

**After (모바일 <sm):**
```
[Logo] [Beta]                                    [🔍]  [👤]
```

#### 3.1.1 SearchTrigger (인라인 검색)

헤더에 인라인으로 배치되는 검색 트리거. 별도 컴포넌트 분리 없이 `SiteHeader` 내부에 직접 작성합니다.

```tsx
// site-header.tsx 내부 (개념)
// 데스크톱: 클릭 가능한 pseudo-input
<button onClick={openSearch} className="hidden sm:flex ...">
  <SearchIcon />
  <span className="text-fg-muted">종목 검색...</span>
  <kbd className="...">{shortcut}</kbd>
</button>

// 모바일: 아이콘만
<button onClick={openSearch} className="sm:hidden ...">
  <SearchIcon />
</button>
```

- **동작**: 클릭 → `setSearchOpen(true)` → 기존 SearchModal 오픈
- **`⌘K`**: 기존 keydown 리스너 그대로 유지
- **데스크톱 input 스타일**: `rounded-lg bg-bg-muted border border-border px-3 py-1.5` 외관이지만 실제 `<button>`
- **kbd 배지**: `rounded border border-border bg-bg px-1.5 py-0.5 text-[10px] text-fg-muted`

#### 3.1.2 UserAvatar (유저 아이콘)

`user-menu.tsx`를 축소하여 아바타 아이콘만 남깁니다.

```tsx
// user-menu.tsx 변경
if (!user) {
  return <Link href="/auth/login">로그인</Link>;
}

// 인증 시: 이메일 이니셜 원형 아바타 → /my 링크
const initial = user.email?.charAt(0).toUpperCase() ?? '?';
return (
  <Link href="/my" className="h-8 w-8 rounded-full bg-primary flex items-center justify-center text-sm font-bold text-primary-fg">
    {initial}
  </Link>
);
```

- **비인증**: `"로그인"` 텍스트 링크 (기존과 유사, border 스타일 유지)
- **인증**: 32px 원형 아바타 + 이니셜, 클릭 시 `/my` 이동
- **로딩**: 32px 원형 스켈레톤 (`animate-pulse rounded-full bg-bg-muted`)

---

### 3.2 FloatingToolbox

#### 3.2.1 파일 위치

`apps/web/src/components/layout/floating-toolbox.tsx`

#### 3.2.2 구조

```tsx
'use client';

export function FloatingToolbox() {
  const [open, setOpen] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);
  const { user, signOut } = useAuth();
  const { theme, setTheme } = useTheme();
  const router = useRouter();
  const [scrolling, setScrolling] = useState(false);

  // 외부 클릭 닫기
  useEffect(() => { ... clickOutside handler ... }, [open]);

  // ESC 닫기
  useEffect(() => { ... keydown Escape handler ... }, [open]);

  // 스크롤 감지 → 반투명
  useEffect(() => { ... scroll handler with debounce ... }, []);

  // 로그아웃 핸들러
  const handleSignOut = async () => {
    await signOut();
    setOpen(false);
    router.push('/');
    router.refresh();
  };

  return (
    <>
      {/* 패널 */}
      {open && (
        <div ref={panelRef} className="fixed right-4 bottom-20 z-35 ...">
          {/* 테마 전환 */}
          <div>
            <span>테마</span>
            <div className="flex gap-1">
              <button onClick={() => setTheme('light')} className={theme === 'light' ? 'active' : ''}>
                ☀️ 라이트
              </button>
              <button onClick={() => setTheme('dark')} className={theme === 'dark' ? 'active' : ''}>
                🌙 다크
              </button>
            </div>
          </div>

          {/* 피드백 */}
          <Link href="/feedback">💬 피드백 보내기</Link>

          {/* 로그아웃 (인증 시만) */}
          {user && <button onClick={handleSignOut}>로그아웃</button>}
        </div>
      )}

      {/* FAB */}
      <button
        onClick={() => setOpen((v) => !v)}
        className={cn(
          "fixed right-4 bottom-4 z-30 h-12 w-12 rounded-full shadow-lg transition-all",
          scrolling && !open ? 'opacity-60' : 'opacity-100',
        )}
      >
        <SettingsIcon />
      </button>
    </>
  );
}
```

#### 3.2.3 패널 레이아웃

```
┌──────────────────────────────┐
│  테마                        │
│  [☀️ 라이트] [🌙 다크]       │  ← 세그먼트 버튼
├──────────────────────────────┤
│  💬  피드백 보내기     →     │  ← Link /feedback
├──────────────────────────────┤
│  🚪  로그아웃                │  ← 인증 시만 (red text)
└──────────────────────────────┘
                    [⚙️]  ← FAB
```

#### 3.2.4 스타일

| 요소 | 스타일 |
|------|--------|
| FAB 버튼 | `h-12 w-12 rounded-full bg-primary text-primary-fg shadow-lg hover:shadow-xl` |
| FAB 아이콘 | 톱니바퀴 SVG (Heroicons `Cog6ToothIcon` 스타일) |
| 패널 | `w-56 rounded-xl border border-border bg-bg-surface shadow-2xl` |
| 패널 열기 | `animate: scale(0.95,1) + opacity(0,1)` 200ms ease-out |
| 테마 세그먼트 활성 | `bg-primary text-primary-fg rounded-lg` |
| 테마 세그먼트 비활성 | `bg-bg-muted text-fg-secondary rounded-lg hover:bg-bg-muted/80` |
| 피드백 행 | `hover:bg-bg-muted rounded-lg px-3 py-2.5` |
| 로그아웃 행 | `text-danger hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg px-3 py-2.5` |

#### 3.2.5 z-index 계층

| 요소 | z-index | Tailwind |
|------|---------|----------|
| 헤더 (sticky) | 40 | `z-40` |
| 툴박스 패널 | 35 | `z-[35]` |
| FAB | 30 | `z-30` |
| 스낵바 | 50 | `z-50` |
| 검색 모달 | 50 | `z-50` |

#### 3.2.6 스크롤 반투명

```typescript
// 스크롤 시작 → opacity-60, 스크롤 멈춤 후 1초 → opacity-100 복귀
// hover 시 즉시 opacity-100
useEffect(() => {
  let timer: ReturnType<typeof setTimeout>;
  const onScroll = () => {
    setScrolling(true);
    clearTimeout(timer);
    timer = setTimeout(() => setScrolling(false), 1000);
  };
  window.addEventListener('scroll', onScroll, { passive: true });
  return () => {
    window.removeEventListener('scroll', onScroll);
    clearTimeout(timer);
  };
}, []);
```

---

### 3.3 Snackbar Toast

#### 3.3.1 Zustand Store

**파일**: `apps/web/src/stores/use-snackbar-store.ts`

```typescript
import { create } from 'zustand';

interface SnackbarState {
  message: string | null;
  show: (message: string) => void;
  hide: () => void;
}

export const useSnackbarStore = create<SnackbarState>((set) => ({
  message: null,
  show: (message) => set({ message }),
  hide: () => set({ message: null }),
}));
```

- 단순 `message | null` 상태
- `show(msg)` 호출 시 이전 메시지 대체 (스택 불필요)
- 컴포넌트 레벨에서 3초 타이머로 auto-dismiss

#### 3.3.2 Snackbar 컴포넌트

**파일**: `apps/web/src/components/ui/snackbar.tsx`

```tsx
'use client';

import { useEffect } from 'react';
import { useSnackbarStore } from '@/stores/use-snackbar-store';

export function Snackbar() {
  const message = useSnackbarStore((s) => s.message);
  const hide = useSnackbarStore((s) => s.hide);

  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(hide, 3000);
    return () => clearTimeout(timer);
  }, [message, hide]);

  if (!message) return null;

  return (
    <div className="fixed bottom-6 left-1/2 z-50 -translate-x-1/2 animate-slide-up">
      <div className="flex items-center gap-3 rounded-lg bg-fg px-4 py-3 text-sm text-bg shadow-lg">
        <span>{message}</span>
        <button onClick={hide} className="...">✕</button>
      </div>
    </div>
  );
}
```

#### 3.3.3 스타일

| 속성 | 값 |
|------|-----|
| 위치 | `fixed bottom-6 left-1/2 -translate-x-1/2` |
| z-index | `z-50` |
| 배경 | `bg-fg` (다크 테마에서 밝은 배경, 라이트에서 어두운 배경 — 반전) |
| 텍스트 | `text-bg` (배경 반전 텍스트) |
| 라운딩 | `rounded-lg` |
| 그림자 | `shadow-lg` |
| 애니메이션 | 커스텀 `animate-slide-up`: translateY(100%) → translateY(0) + opacity(0→1), 200ms |
| auto-dismiss | 3초 |

#### 3.3.4 CSS 애니메이션

`globals.css`에 추가:

```css
@keyframes slide-up {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(1rem);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

.animate-slide-up {
  animation: slide-up 200ms ease-out;
}
```

#### 3.3.5 스낵바 호출 지점

| 파일 | 트리거 | 메시지 |
|------|--------|--------|
| `bookmark-button.tsx` | addMutation onSuccess | "북마크에 추가했습니다" |
| `bookmark-button.tsx` | removeMutation onSuccess | "북마크를 해제했습니다" |
| `bookmark-card.tsx` | removeMutation onSuccess | "북마크를 해제했습니다" |
| `notification-setting-modal.tsx` | upsertMutation onSuccess | "알림이 설정되었습니다" |
| `notification-setting-modal.tsx` | deleteMutation onSuccess | "알림이 해제되었습니다" |
| `notification-section.tsx` | deleteMutation onSuccess | "알림이 해제되었습니다" |

**구현 패턴:**

```typescript
// bookmark-button.tsx 예시
const showSnackbar = useSnackbarStore((s) => s.show);

function handleClick() {
  if (!user) { setShowAuthModal(true); return; }
  if (isBookmarked) {
    removeMutation.mutate(ticker, {
      onSuccess: () => showSnackbar('북마크를 해제했습니다'),
    });
  } else {
    addMutation.mutate(ticker, {
      onSuccess: () => showSnackbar('북마크에 추가했습니다'),
    });
  }
}
```

```typescript
// notification-setting-modal.tsx 예시
const showSnackbar = useSnackbarStore((s) => s.show);

function handleSave() {
  if (!bookmarkCheck?.bookmarked) addBookmarkMutation.mutate(ticker);
  upsertMutation.mutate({ ticker, req: { ... } }, {
    onSuccess: () => { showSnackbar('알림이 설정되었습니다'); onClose(); },
  });
}

function handleDelete() {
  deleteMutation.mutate(ticker, {
    onSuccess: () => { showSnackbar('알림이 해제되었습니다'); onClose(); },
  });
}
```

---

### 3.4 마이페이지 UI 리디자인

현재 마이페이지는 대시보드/종목 상세와 동일한 `rounded-lg border border-border bg-bg-surface` 카드 패턴을 사용하고 있어 전체적으로 밋밋하고 차별성이 없습니다. 마이페이지는 "내 정보" 중심이므로 **더 따뜻하고 개인화된 느낌**의 카드 스타일로 차별화합니다.

#### 3.4.1 디자인 원칙

1. **카드 차별화**: 기존 `border-border bg-bg-surface` 대신 subtle gradient 배경, 더 큰 패딩, 부드러운 그림자 사용
2. **정보 밀도 향상**: 좁은 패딩 → 여유 있는 간격, 더 큰 폰트 크기
3. **색상 액센트**: 섹션별 액센트 컬러 (프로필=primary, 북마크=amber, 알림=blue, 계정=red)
4. **일관된 섹션 헤더**: 아이콘 + 제목 + 설명문 패턴

#### 3.4.2 MyPage (page.tsx) 레이아웃

```tsx
// Before: max-w-2xl space-y-6
// After: max-w-3xl space-y-8

<div className="mx-auto max-w-3xl px-4 py-8 sm:px-6">
  {/* 페이지 타이틀 제거 — 프로필 카드가 대신 역할 */}
  <ProfileSection user={user} />

  <section className="mt-8">
    <SectionHeader icon={StarIcon} title="내 북마크" count={data?.total} />
    <BookmarkGrid />
  </section>

  <section className="mt-8">
    <SectionHeader icon={BellIcon} title="알림 설정" count={settings?.length} />
    <NotificationSection />
  </section>

  <AccountSection />
</div>
```

- `max-w-2xl` → `max-w-3xl` (더 넓은 레이아웃)
- `space-y-6` → 명시적 `mt-8` (섹션 간 더 넓은 간격)
- "마이페이지" h1 타이틀 제거 (프로필 카드가 역할 대체)
- `SectionHeader` 공통 패턴: 아이콘 + 제목 + 건수

#### 3.4.3 ProfileSection 리디자인

**Before**: 좁은 카드, 작은 아바타(56px), 로그아웃 버튼 포함

**After**:

```tsx
<div className="rounded-2xl bg-gradient-to-br from-primary/5 to-primary/10 p-6 dark:from-primary/10 dark:to-primary/5">
  <div className="flex items-center gap-5">
    {/* 큰 아바타 */}
    <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary text-2xl font-bold text-primary-fg shadow-md">
      {initial}
    </div>
    <div>
      <p className="text-lg font-semibold text-fg">{email}</p>
      <p className="mt-1 text-sm text-fg-muted">
        가입일: {createdAt}
      </p>
    </div>
  </div>
</div>
```

- **로그아웃 버튼 제거** (플로팅 툴박스로 이동)
- `onSignOut` prop 제거
- 아바타 크기: `h-14 w-14` → `h-16 w-16`, 폰트 `text-xl` → `text-2xl`
- 카드 배경: `border border-border bg-bg-surface` → `bg-gradient-to-br from-primary/5 to-primary/10` (브랜드 그라데이션)
- 라운딩: `rounded-lg` → `rounded-2xl`
- 패딩: `p-5` → `p-6`

#### 3.4.4 BookmarkCard 리디자인

**Before**: 단순 border 카드, X 버튼 항상 표시

**After**:

```tsx
<div className="group relative rounded-xl bg-bg-surface p-4 shadow-sm transition-all hover:shadow-md dark:bg-bg-surface/80">
  <Link href={`/stock/${ticker}`} className="block">
    <div className="flex items-center justify-between">
      <div>
        <span className="text-base font-bold text-fg">{ticker}</span>
        <p className="mt-0.5 truncate text-xs text-fg-muted">{name}</p>
      </div>
      <div className="text-right">
        <span className="text-sm font-medium tabular-nums text-fg">${price}</span>
        <p className={cn("text-xs font-semibold tabular-nums", up ? 'text-success' : 'text-danger')}>
          {up ? '+' : ''}{changePercent}%
        </p>
      </div>
    </div>
  </Link>
  {/* 삭제 버튼: hover에만 표시 (모바일은 항상) */}
  <button className="absolute -top-2 -right-2 h-6 w-6 rounded-full bg-bg shadow-sm border border-border opacity-0 group-hover:opacity-100 transition-opacity sm:opacity-0 max-sm:opacity-100">
    <XIcon />
  </button>
</div>
```

- **카드 스타일**: `border border-border bg-bg-surface` → `bg-bg-surface shadow-sm hover:shadow-md` (보더 제거, 그림자 기반)
- **라운딩**: `rounded-lg` → `rounded-xl`
- **가격/등락률 배치**: 우측 정렬로 변경 (좌: 티커+이름, 우: 가격+%)
- **삭제 버튼**: 카드 내부 → 카드 우상단 오버레이 (`absolute -top-2 -right-2`), hover에만 노출
- **그리드**: 2열 유지, `gap-3` → `gap-4`

#### 3.4.5 BookmarkGrid 빈 상태

**Before**: dashed border + 별 아이콘

**After**:

```tsx
<div className="rounded-xl bg-bg-muted/50 p-10 text-center">
  <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-amber-100 dark:bg-amber-900/20">
    <StarIcon className="h-7 w-7 text-amber-500" />
  </div>
  <p className="mt-4 text-sm font-medium text-fg-secondary">
    아직 북마크한 종목이 없습니다
  </p>
  <p className="mt-1.5 text-xs text-fg-muted">
    종목 상세에서 ☆ 버튼을 눌러 관심 종목을 추가해 보세요
  </p>
</div>
```

- dashed border → `bg-bg-muted/50 rounded-xl` 배경
- 아이콘을 원형 배경(`bg-amber-100`)으로 감싸서 강조
- 텍스트 간격 넓힘

#### 3.4.6 NotificationSection 리디자인

**Before**: border-only 행 리스트

**After**: 카드 스타일 + 상태 구분 강화

```tsx
{data.map((s) => (
  <div
    key={s.ticker}
    className={cn(
      "rounded-xl p-4 transition-colors",
      s.enabled
        ? "bg-bg-surface shadow-sm"
        : "bg-bg-muted/50"
    )}
  >
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-3">
        <span className="text-base font-bold text-fg">{s.ticker}</span>
        <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-900/30 dark:text-blue-400">
          ±{s.priceChangeThreshold ?? 5}%
        </span>
        {s.onNewNews && <NewsBadge />}
        {!s.enabled && <DisabledBadge />}
      </div>
      <div className="flex items-center gap-2">
        <EditButton />
        <DeleteButton />
      </div>
    </div>
  </div>
))}
```

- **활성 알림**: `bg-bg-surface shadow-sm` (밝은 카드 + 그림자)
- **비활성 알림**: `bg-bg-muted/50` (회색 배경으로 시각적 구분)
- 라운딩: `rounded-lg` → `rounded-xl`
- 패딩: `px-4 py-3` → `p-4`
- 액션 버튼 크기 증가: `h-7 w-7` → `h-8 w-8`

#### 3.4.7 AccountSection 리디자인

**Before**: 중앙 정렬 "회원 탈퇴" 텍스트 링크만

**After**:

```tsx
<section className="mt-12">
  <div className="rounded-xl border border-red-200 bg-red-50/50 p-5 dark:border-red-900/50 dark:bg-red-950/20">
    <div className="flex items-center gap-3">
      <div className="flex h-9 w-9 items-center justify-center rounded-full bg-red-100 dark:bg-red-900/30">
        <ExclamationIcon className="h-5 w-5 text-danger" />
      </div>
      <div className="flex-1">
        <p className="text-sm font-medium text-fg">계정 삭제</p>
        <p className="mt-0.5 text-xs text-fg-muted">
          탈퇴 시 모든 북마크와 알림 설정이 영구 삭제됩니다
        </p>
      </div>
      <button
        onClick={() => setShowDeleteModal(true)}
        className="rounded-lg border border-red-300 px-3 py-1.5 text-sm font-medium text-danger hover:bg-red-100 dark:border-red-800 dark:hover:bg-red-900/30"
      >
        회원 탈퇴
      </button>
    </div>
  </div>
</section>
```

- `mt-12` 으로 다른 섹션과 더 넓은 간격
- red 테마 카드: `border-red-200 bg-red-50/50`
- 경고 아이콘 + 설명문 추가
- 버튼이 카드 내부로 이동 (기존: 독립 텍스트 링크)

#### 3.4.8 SectionHeader 공통 패턴

마이페이지 내부 인라인 JSX로 작성 (별도 컴포넌트 분리 불필요):

```tsx
<div className="mb-4 flex items-center gap-2">
  <IconComponent className="h-5 w-5 text-fg-muted" />
  <h2 className="text-base font-semibold text-fg">섹션 제목</h2>
  {count != null && count > 0 && (
    <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
      {count}
    </span>
  )}
</div>
```

---

## 4. State Management

### 4.1 신규 상태

| Store | 타입 | 용도 |
|-------|------|------|
| `useSnackbarStore` | Zustand | 스낵바 메시지 show/hide |

### 4.2 기존 상태 (변경 없음)

| Store | 용도 | 영향 |
|-------|------|------|
| `useAuth()` | 인증 상태 | FloatingToolbox에서 user/signOut 참조 |
| `useTheme()` | 테마 상태 | FloatingToolbox에서 theme/setTheme 참조 |
| React Query (bookmarks) | 북마크 CRUD | mutation onSuccess에 스낵바 추가 |
| React Query (notifications) | 알림 CRUD | mutation onSuccess에 스낵바 추가 |

---

## 5. Implementation Order

### Step 1: Snackbar 인프라 (의존성 없음)
1. `stores/use-snackbar-store.ts` — Zustand store 생성
2. `components/ui/snackbar.tsx` — Snackbar 컴포넌트
3. `globals.css` — `animate-slide-up` 키프레임
4. `layout.tsx` — `<Snackbar />` 추가

### Step 2: 헤더 간소화 + 유저 아이콘
5. `user-menu.tsx` — 아바타 아이콘으로 축소
6. `site-header.tsx` — ThemeSwitcher/UserMenu 제거, SearchTrigger+UserAvatar 인라인

### Step 3: 플로팅 툴박스
7. `components/layout/floating-toolbox.tsx` — FAB + 패널
8. `layout.tsx` — `<FloatingToolbox />` 추가

### Step 4: 스낵바 연결
9. `bookmark-button.tsx` — 추가/해제 스낵바
10. `bookmark-card.tsx` — 해제 스낵바
11. `notification-setting-modal.tsx` — 저장/삭제 스낵바
12. `notification-section.tsx` — 삭제 스낵바

### Step 5: 마이페이지 UI 리디자인
13. `profile-section.tsx` — 리디자인 + 로그아웃 제거
14. `bookmark-card.tsx` — 카드 UI 리디자인
15. `bookmark-grid.tsx` — 그리드/빈 상태 개선
16. `notification-section.tsx` — 카드 스타일 통일
17. `account-section.tsx` — 위험 액션 시각 분리
18. `my/page.tsx` — 레이아웃/타이포 개선

### Step 6: 검증
19. `make web-check` 통과 확인
20. 브라우저 테스트 (3테마, 모바일/데스크톱, 인증/비인증)

---

## 6. File Change Summary

| # | 파일 | 변경 유형 | Step |
|---|------|-----------|------|
| 1 | `src/stores/use-snackbar-store.ts` | **신규** | 1 |
| 2 | `src/components/ui/snackbar.tsx` | **신규** | 1 |
| 3 | `src/app/globals.css` | 수정 (키프레임 추가) | 1 |
| 4 | `src/app/layout.tsx` | 수정 (Snackbar+FloatingToolbox 추가) | 1,3 |
| 5 | `src/features/auth/user-menu.tsx` | 수정 (아바타 축소) | 2 |
| 6 | `src/components/layout/site-header.tsx` | 수정 (간소화) | 2 |
| 7 | `src/components/layout/floating-toolbox.tsx` | **신규** | 3 |
| 8 | `src/features/bookmark/bookmark-button.tsx` | 수정 (스낵바) | 4 |
| 9 | `src/features/my-page/bookmark-card.tsx` | 수정 (스낵바+UI) | 4,5 |
| 10 | `src/features/stock-detail/notification-setting-modal.tsx` | 수정 (스낵바) | 4 |
| 11 | `src/features/my-page/notification-section.tsx` | 수정 (스낵바+UI) | 4,5 |
| 12 | `src/features/my-page/profile-section.tsx` | 수정 (리디자인) | 5 |
| 13 | `src/features/my-page/bookmark-grid.tsx` | 수정 (UI) | 5 |
| 14 | `src/features/my-page/account-section.tsx` | 수정 (UI) | 5 |
| 15 | `src/app/my/page.tsx` | 수정 (레이아웃) | 5 |

**신규 파일 3개** / **수정 파일 12개** / BE 변경 없음

---

## 7. Acceptance Criteria

| ID | 항목 | 검증 방법 |
|----|------|-----------|
| AC-01 | 헤더에 검색 트리거 + 유저 아이콘만 표시 | 시각 확인 |
| AC-02 | 데스크톱: 검색 pseudo-input 표시, 모바일: 돋보기만 | 반응형 확인 |
| AC-03 | ⌘K → SearchModal 정상 오픈 | 키보드 테스트 |
| AC-04 | FAB 우하단 항상 표시, 스크롤 시 반투명 | 스크롤 테스트 |
| AC-05 | FAB 클릭 → 패널 열림, 테마 전환 동작 | 클릭 테스트 |
| AC-06 | 패널 외부 클릭/ESC → 패널 닫힘 | 클릭+키보드 테스트 |
| AC-07 | 인증 시 패널에 로그아웃 표시, 비인증 시 숨김 | 상태별 확인 |
| AC-08 | 북마크 추가 → "북마크에 추가했습니다" 스낵바 | 종목 상세 테스트 |
| AC-09 | 북마크 해제 → "북마크를 해제했습니다" 스낵바 | 종목 상세 + 마이페이지 |
| AC-10 | 알림 설정 → "알림이 설정되었습니다" 스낵바 | 종목 상세 테스트 |
| AC-11 | 알림 삭제 → "알림이 해제되었습니다" 스낵바 | 종목 상세 + 마이페이지 |
| AC-12 | 마이페이지 프로필 그라데이션 카드, 로그아웃 없음 | 시각 확인 |
| AC-13 | 마이페이지 북마크 카드 그림자 기반, 가격 우측 정렬 | 시각 확인 |
| AC-14 | 마이페이지 알림 카드 활성/비활성 시각 구분 | 시각 확인 |
| AC-15 | 마이페이지 계정 섹션 red 경고 카드 | 시각 확인 |
| AC-16 | 3테마 (light/dark) 전체 정상 렌더링 | 테마 전환 테스트 |
| AC-17 | `make web-check` 통과 | CLI |

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-24 | Initial design document | wonseok-han |
