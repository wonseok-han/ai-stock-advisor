package com.aistockadvisor.stock.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 종목 상세 통합 응답 (design §4.2).
 * Phase 1: news / aiSignal 은 항상 null (Phase 2 에서 구현).
 * 블록별 실패 시 해당 필드 null + errors 에 사유 + partial=true.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record StockDetailResponse(
        StockProfile profile,
        Quote quote,
        List<Candle> candles,
        IndicatorSnapshot indicators,
        List<Object> news,
        Object aiSignal,
        Disclaimer disclaimer,
        boolean partial,
        List<BlockError> errors,
        Meta meta
) {
    public record Disclaimer(String page, String version, String text) {
    }

    public record BlockError(String block, String code, String message) {
    }

    public record Meta(String requestId, OffsetDateTime timestamp) {
    }
}
