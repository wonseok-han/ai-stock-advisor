import { env } from '@/lib/env';

import type { ApiErrorBody } from '@/types/stock';

/**
 * 얇은 fetch 래퍼. BE 통일 에러 포맷을 ApiError 로 throw.
 * 서버/클라 양쪽에서 사용 (Next.js App Router RSC + Client).
 */
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
  const res = await fetch(url, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.headers ?? {}),
    },
    cache: init?.cache ?? 'no-store',
  });

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

  return (await res.json()) as T;
}
