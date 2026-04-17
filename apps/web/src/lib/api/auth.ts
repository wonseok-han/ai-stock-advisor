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

export async function reactivateAccount(email: string): Promise<boolean> {
  try {
    await apiFetch('/auth/reactivate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    });
    return true;
  } catch {
    return false;
  }
}
