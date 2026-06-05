'use client';

import Link from 'next/link';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';

import { useAuth } from '@/features/auth/auth-provider';
import { usePwaInstall } from '@/features/pwa/use-pwa-install';
import { useTheme } from '@/components/theme/theme-provider';
import { cn } from '@/lib/cn';

import type { Theme } from '@/components/theme/theme-provider';

export function FloatingToolbox() {
  const [open, setOpen] = useState(false);
  const [closing, setClosing] = useState(false);
  const [scrolling, setScrolling] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);
  const fabRef = useRef<HTMLButtonElement>(null);
  const { user, signOut } = useAuth();
  const { theme, setTheme } = useTheme();
  const { canInstall, promptInstall } = usePwaInstall();
  const router = useRouter();

  const close = useCallback(() => {
    setClosing(true);
    setTimeout(() => {
      setOpen(false);
      setClosing(false);
    }, 200);
  }, []);

  const toggle = useCallback(() => {
    if (closing) return;
    if (open) {
      close();
    } else {
      setOpen(true);
    }
  }, [open, closing, close]);

  useEffect(() => {
    if (!open || closing) return;
    const handleClick = (e: MouseEvent) => {
      if (
        panelRef.current?.contains(e.target as Node) ||
        fabRef.current?.contains(e.target as Node)
      ) return;
      close();
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [open, closing, close]);

  useEffect(() => {
    if (!open || closing) return;
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close();
    };
    document.addEventListener('keydown', handleKey);
    return () => document.removeEventListener('keydown', handleKey);
  }, [open, closing, close]);

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

  const handleSignOut = async () => {
    await signOut();
    close();
    router.push('/');
    router.refresh();
  };

  const themeOptions: { value: Theme; label: string; icon: React.ReactNode }[] = [
    {
      value: 'light',
      label: '라이트',
      icon: (
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 3v2.25m6.364.386l-1.591 1.591M21 12h-2.25m-.386 6.364l-1.591-1.591M12 18.75V21m-4.773-4.227l-1.591 1.591M5.25 12H3m4.227-4.773L5.636 5.636M15.75 12a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0z" />
        </svg>
      ),
    },
    {
      value: 'dark',
      label: '다크',
      icon: (
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" d="M21.752 15.002A9.72 9.72 0 0118 15.75c-5.385 0-9.75-4.365-9.75-9.75 0-1.33.266-2.597.748-3.752A9.753 9.753 0 003 11.25C3 16.635 7.365 21 12.75 21a9.753 9.753 0 009.002-5.998z" />
        </svg>
      ),
    },
  ];

  return (
    <>
      {/* 패널 */}
      {open && (
        <div
          ref={panelRef}
          className={cn(
            'fixed right-4 bottom-20 z-[35] w-56 origin-bottom-right rounded-xl border border-border bg-bg-surface shadow-2xl',
            closing ? 'animate-toolbox-close' : 'animate-toolbox-open',
          )}
        >
          {/* 테마 */}
          <div className="border-b border-border p-3">
            <span className="text-xs font-medium text-fg-muted">테마</span>
            <div className="mt-2 flex gap-1">
              {themeOptions.map((opt) => (
                <button
                  key={opt.value}
                  onClick={() => setTheme(opt.value)}
                  className={cn(
                    'flex flex-1 cursor-pointer items-center justify-center gap-1.5 rounded-lg px-3 py-2 text-xs font-medium transition-colors',
                    theme === opt.value
                      ? 'bg-primary text-primary-fg'
                      : 'bg-bg-muted text-fg-secondary hover:bg-bg-muted/80',
                  )}
                >
                  {opt.icon}
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* 앱 설치 (설치 가능할 때만) */}
          {canInstall && (
            <button
              onClick={async () => {
                await promptInstall();
                close();
              }}
              className="flex w-full cursor-pointer items-center gap-2.5 border-b border-border px-3 py-2.5 text-sm text-fg-secondary transition-colors hover:bg-bg-muted"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 16.5m0 0L7.5 12m4.5 4.5V3" />
              </svg>
              앱 설치
            </button>
          )}

          {/* 피드백 */}
          <Link
            href="/feedback"
            onClick={close}
            className="flex items-center gap-2.5 px-3 py-2.5 text-sm text-fg-secondary transition-colors hover:bg-bg-muted"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" d="M8.625 12a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H8.25m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0H12m4.125 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm0 0h-.375M21 12c0 4.556-4.03 8.25-9 8.25a9.764 9.764 0 01-2.555-.337A5.972 5.972 0 015.41 20.97a5.969 5.969 0 01-.474-.065 4.48 4.48 0 00.978-2.025c.09-.457-.133-.901-.467-1.226C3.93 16.178 3 14.189 3 12c0-4.556 4.03-8.25 9-8.25s9 3.694 9 8.25z" />
            </svg>
            피드백 보내기
          </Link>

          {/* 로그아웃 */}
          {user && (
            <button
              onClick={handleSignOut}
              className="flex w-full cursor-pointer items-center gap-2.5 border-t border-border px-3 py-2.5 text-sm text-danger transition-colors hover:bg-red-50 dark:hover:bg-red-900/20"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 9V5.25A2.25 2.25 0 0013.5 3h-6a2.25 2.25 0 00-2.25 2.25v13.5A2.25 2.25 0 007.5 21h6a2.25 2.25 0 002.25-2.25V15m3 0l3-3m0 0l-3-3m3 3H9" />
              </svg>
              로그아웃
            </button>
          )}
        </div>
      )}

      {/* FAB */}
      <button
        ref={fabRef}
        onClick={toggle}
        className={cn(
          'fixed right-4 bottom-4 z-30 flex h-12 w-12 cursor-pointer items-center justify-center rounded-full bg-primary text-primary-fg shadow-lg transition-all hover:opacity-100 hover:shadow-xl',
          scrolling && !open ? 'opacity-60' : 'opacity-100',
        )}
        aria-label={open ? '툴박스 닫기' : '툴박스 열기'}
      >
        <svg className={cn('h-5 w-5 transition-transform duration-200', open && 'rotate-90')} fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.325.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 011.37.49l1.296 2.247a1.125 1.125 0 01-.26 1.431l-1.003.827c-.293.241-.438.613-.43.992a7.723 7.723 0 010 .255c-.008.378.137.75.43.991l1.004.827c.424.35.534.955.26 1.43l-1.298 2.247a1.125 1.125 0 01-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.47 6.47 0 01-.22.128c-.331.183-.581.495-.644.869l-.213 1.281c-.09.543-.56.941-1.11.941h-2.594c-.55 0-1.019-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 01-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 01-1.369-.49l-1.297-2.247a1.125 1.125 0 01.26-1.431l1.004-.827c.292-.24.437-.613.43-.991a6.932 6.932 0 010-.255c.007-.38-.138-.751-.43-.992l-1.004-.827a1.125 1.125 0 01-.26-1.43l1.297-2.247a1.125 1.125 0 011.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.086.22-.128.332-.183.582-.495.644-.869l.214-1.28z" />
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      </button>
    </>
  );
}
