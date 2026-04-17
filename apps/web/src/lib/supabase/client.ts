import { createBrowserClient } from '@supabase/ssr';

import { env } from '@/lib/env';

import type { SupabaseClient } from '@supabase/supabase-js';

/**
 * 브라우저 전용 Supabase 클라이언트.
 * 쿠키 기반 세션 관리 (@supabase/ssr).
 * 환경변수 미설정 시 null 반환 (빌드 안전).
 */
export function createClient(): SupabaseClient | null {
  if (!env.supabaseUrl || !env.supabasePublishableKey) return null;
  return createBrowserClient(env.supabaseUrl, env.supabasePublishableKey);
}
