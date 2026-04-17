import { apiFetch } from '@/lib/api/client';

import type { MeResponse } from '@/types/auth';

export function fetchMe() {
  return apiFetch<MeResponse>('/me');
}

export function deleteAccount(reason?: string): Promise<void> {
  return apiFetch('/me', {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: reason ? JSON.stringify({ reason }) : undefined,
  });
}
