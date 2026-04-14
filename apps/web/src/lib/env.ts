/**
 * 환경변수 타입 가드. 빌드/런타임에 누락을 빠르게 감지.
 * NEXT_PUBLIC_* 만 브라우저 노출 (design §6.3 / CLAUDE.md).
 */

function required(name: string, value: string | undefined): string {
  if (!value || value.length === 0) {
    throw new Error(`Missing env: ${name}`);
  }
  return value;
}

export const env = {
  apiBaseUrl: required(
    'NEXT_PUBLIC_API_BASE_URL',
    process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1',
  ),
} as const;
