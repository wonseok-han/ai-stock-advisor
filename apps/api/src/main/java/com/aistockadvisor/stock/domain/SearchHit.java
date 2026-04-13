package com.aistockadvisor.stock.domain;

/**
 * 검색 결과 단일 항목. Finnhub /search 결과를 매핑.
 * Finnhub 무료 /search 는 exchange 를 별도로 주지 않으므로 null 허용.
 */
public record SearchHit(
        String ticker,
        String name,
        String exchange,
        String matchType
) {
}
