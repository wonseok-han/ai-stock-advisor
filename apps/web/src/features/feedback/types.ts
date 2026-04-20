/**
 * 피드백 유형.
 * DB CHECK 제약과 동일.
 */
export type FeedbackType = 'bug' | 'question' | 'suggestion';

export const FEEDBACK_TYPE_LABELS: Record<FeedbackType, string> = {
  bug: '버그 신고',
  question: '문의',
  suggestion: '제안',
};

/**
 * Supabase insert payload.
 * DB 스키마의 subset — status/created_at/updated_at 는 자동.
 */
export interface FeedbackInsert {
  user_id: string | null;
  email: string;
  type: FeedbackType;
  subject: string;
  body: string;
  url: string | null;
  user_agent: string | null;
}

export const FEEDBACK_LIMITS = {
  SUBJECT_MIN: 1,
  SUBJECT_MAX: 100,
  BODY_MIN: 10,
  BODY_MAX: 2000,
  COOLDOWN_MS: 60_000,
} as const;

export const FEEDBACK_COOLDOWN_KEY = 'feedback:lastSubmittedAt';
