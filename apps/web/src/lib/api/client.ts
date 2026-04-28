import { getAccessToken as getCachedToken, setAccessToken } from '@/lib/auth/token-store';
import { env } from '@/lib/env';

import type { ApiErrorBody } from '@/types/stock';

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly requestId: string | undefined;

  constructor(status: number, body: ApiErrorBody) {
    super(body.error?.message ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = body.error?.code ?? 'UNKNOWN';
    this.requestId = body.error?.requestId;
  }
}

export async function apiFetch<T>(
  path: string,
  init?: RequestInit & { signal?: AbortSignal },
): Promise<T> {
  const url = `${env.apiBaseUrl}${path}`;
  const token = typeof window !== 'undefined' ? getCachedToken() : null;
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...(init?.headers as Record<string, string> ?? {}),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(url, {
    ...init,
    headers,
    cache: init?.cache ?? 'no-store',
  });

  // 401 → 토큰 갱신 시도 후 1회 재시도
  if (res.status === 401 && token && typeof window !== 'undefined') {
    try {
      const { createClient } = await import('@/lib/supabase/client');
      const supabase = createClient();
      if (!supabase) throw new Error('no client');
      const { data } = await supabase.auth.refreshSession();
      if (data.session?.access_token) {
        setAccessToken(data.session.access_token);
        headers['Authorization'] = `Bearer ${data.session.access_token}`;
        const retry = await fetch(url, { ...init, headers, cache: init?.cache ?? 'no-store' });
        if (retry.ok) {
          return (await retry.json()) as T;
        }
      }
    } catch {
      // 갱신 실패 — 아래에서 원래 에러 throw
    }
  }

  if (!res.ok) {
    let body: ApiErrorBody | undefined;
    try {
      body = (await res.json()) as ApiErrorBody;
    } catch {
      // ignore
    }
    throw new ApiError(
      res.status,
      body ?? {
        error: {
          code: 'UNKNOWN',
          message: res.statusText,
          requestId: '',
          timestamp: new Date().toISOString(),
        },
      },
    );
  }

  if (res.status === 204 || res.headers.get('content-length') === '0') {
    return undefined as T;
  }

  return (await res.json()) as T;
}
