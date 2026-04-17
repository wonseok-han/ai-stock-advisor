import { apiFetch } from '@/lib/api/client';

import type { NotificationSetting, NotificationSettingRequest } from '@/types/notification';

export function getNotificationSettings(): Promise<NotificationSetting[]> {
  return apiFetch('/api/v1/notifications/settings');
}

export function upsertNotificationSetting(
  ticker: string,
  req: NotificationSettingRequest,
): Promise<NotificationSetting> {
  return apiFetch(`/api/v1/notifications/settings/${encodeURIComponent(ticker)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
}

export function pushSubscribe(subscription: PushSubscription): Promise<{ subscribed: boolean }> {
  const json = subscription.toJSON();
  return apiFetch('/api/v1/push/subscribe', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      endpoint: json.endpoint,
      keys: {
        p256dh: json.keys?.p256dh ?? '',
        auth: json.keys?.auth ?? '',
      },
    }),
  });
}

export function pushUnsubscribe(endpoint: string): Promise<void> {
  return apiFetch('/api/v1/push/unsubscribe', {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ endpoint }),
  });
}

export function getVapidKey(): Promise<{ publicKey: string }> {
  return apiFetch('/api/v1/push/vapid-key');
}
