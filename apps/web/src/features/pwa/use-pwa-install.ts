'use client';

import { useCallback, useEffect, useState } from 'react';

/** Chrome/Edge 가 발생시키는 beforeinstallprompt 이벤트 (TS 기본 타입에 없어 직접 정의). */
interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

/**
 * PWA 설치 프롬프트 훅.
 * - 설치 가능(beforeinstallprompt 발생, 미설치)할 때만 canInstall=true
 * - 이미 설치(standalone)됐거나 iOS(이벤트 미발생)면 canInstall=false → 버튼 숨김
 */
export function usePwaInstall() {
  const [deferred, setDeferred] = useState<BeforeInstallPromptEvent | null>(null);

  useEffect(() => {
    const onPrompt = (e: Event) => {
      e.preventDefault();
      setDeferred(e as BeforeInstallPromptEvent);
    };
    const onInstalled = () => setDeferred(null);

    window.addEventListener('beforeinstallprompt', onPrompt);
    window.addEventListener('appinstalled', onInstalled);
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt);
      window.removeEventListener('appinstalled', onInstalled);
    };
  }, []);

  const promptInstall = useCallback(async () => {
    if (!deferred) return;
    await deferred.prompt();
    await deferred.userChoice;
    setDeferred(null); // 한 번 띄우면 이벤트는 소비됨
  }, [deferred]);

  return { canInstall: deferred !== null, promptInstall };
}
