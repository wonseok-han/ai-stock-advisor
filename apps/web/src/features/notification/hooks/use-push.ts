'use client';

import { useCallback, useEffect, useState } from 'react';

import { getVapidKey, pushSubscribe, pushUnsubscribe } from '@/lib/api/notifications';
import { useAuth } from '@/features/auth/auth-provider';

function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  return Uint8Array.from(raw, (c) => c.charCodeAt(0));
}

export function usePush() {
  const { user } = useAuth();
  const [permission, setPermission] = useState<NotificationPermission>('default');
  const [isSubscribed, setIsSubscribed] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (typeof window === 'undefined' || !('Notification' in window)) return;
    setPermission(Notification.permission);
  }, []);

  useEffect(() => {
    if (!user || typeof navigator === 'undefined' || !('serviceWorker' in navigator)) return;
    navigator.serviceWorker.ready.then(async (reg) => {
      const sub = await reg.pushManager.getSubscription();
      setIsSubscribed(!!sub);
    });
  }, [user]);

  const subscribe = useCallback(async () => {
    if (!user || loading) return;
    setLoading(true);
    try {
      const perm = await Notification.requestPermission();
      setPermission(perm);
      if (perm !== 'granted') return;

      const reg = await navigator.serviceWorker.ready;
      const { publicKey } = await getVapidKey();
      const sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(publicKey).buffer as ArrayBuffer,
      });
      await pushSubscribe(sub);
      setIsSubscribed(true);
    } catch (e) {
      console.error('Push subscribe failed:', e);
    } finally {
      setLoading(false);
    }
  }, [user, loading]);

  const unsubscribe = useCallback(async () => {
    if (!user || loading) return;
    setLoading(true);
    try {
      const reg = await navigator.serviceWorker.ready;
      const sub = await reg.pushManager.getSubscription();
      if (sub) {
        await pushUnsubscribe(sub.endpoint);
        await sub.unsubscribe();
      }
      setIsSubscribed(false);
    } catch (e) {
      console.error('Push unsubscribe failed:', e);
    } finally {
      setLoading(false);
    }
  }, [user, loading]);

  const supported = typeof window !== 'undefined' && 'Notification' in window && 'serviceWorker' in navigator;

  return { supported, permission, isSubscribed, loading, subscribe, unsubscribe };
}
