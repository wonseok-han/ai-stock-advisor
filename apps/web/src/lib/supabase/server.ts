import { createServerClient } from '@supabase/ssr';
import { cookies } from 'next/headers';

import { env } from '@/lib/env';

import type { SupabaseClient } from '@supabase/supabase-js';

/**
 * 서버 컴포넌트 / Route Handler 전용 Supabase 클라이언트.
 * Next.js cookies() 를 통해 세션 쿠키를 읽고 쓴다.
 * 환경변수 미설정 시 null 반환 (빌드 안전).
 */
export async function createClient(): Promise<SupabaseClient | null> {
  if (!env.supabaseUrl || !env.supabasePublishableKey) return null;

  const cookieStore = await cookies();

  return createServerClient(env.supabaseUrl, env.supabasePublishableKey, {
    cookies: {
      getAll() {
        return cookieStore.getAll();
      },
      setAll(cookiesToSet) {
        try {
          for (const { name, value, options } of cookiesToSet) {
            cookieStore.set(name, value, options);
          }
        } catch {
          // Server Component에서는 set 불가 — 무시
        }
      },
    },
  });
}
