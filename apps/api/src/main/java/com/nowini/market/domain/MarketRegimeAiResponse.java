package com.nowini.market.domain;

/**
 * 시장 국면 AI 해석 응답 (로그인 사용자 전용, /api/v1/market/regime/ai).
 * <p>
 * 동일 지표 스냅샷을 입력으로 Gemini가 생성한 참고 관점. aiSummary는 실패 시 null.
 */
public record MarketRegimeAiResponse(
        String asOf,
        String aiSummary,
        String disclaimer
) {}
