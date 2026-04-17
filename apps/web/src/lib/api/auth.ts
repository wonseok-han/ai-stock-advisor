import { apiFetch } from '@/lib/api/client';

import type { MeResponse } from '@/types/auth';

export function fetchMe() {
  return apiFetch<MeResponse>('/me');
}
