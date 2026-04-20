package com.aistockadvisor.stock.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MarketStatusResolver 단위 테스트 (M1 ~ M6).
 *
 * <p>결정 매트릭스:
 * <ul>
 *   <li>M1: resolveByPeriod — now 가 [start, end) 내 → OPEN</li>
 *   <li>M2: resolveByPeriod — now 가 end 이후 → CLOSED</li>
 *   <li>M3: resolveByPeriod — now 가 start 이전 → CLOSED</li>
 *   <li>M4: resolveByPeriod(0, 0) — fallback to resolve() (상태 존재 보장)</li>
 *   <li>M5: priceLabel(OPEN, ...) → "실시간 (약 1~2분 지연)"</li>
 *   <li>M6: priceLabel(CLOSED, non-null) → "{M/d} 정규장 종가"</li>
 *   <li>M7: priceLabel(CLOSED, null) → "정규장 종가"</li>
 * </ul>
 */
class MarketStatusResolverTest {

    @Test
    @DisplayName("M1: resolveByPeriod — now 가 [start, end) 내이면 OPEN")
    void m1_nowInsidePeriodReturnsOpen() {
        long now = Instant.now().getEpochSecond();
        long start = now - 3600;
        long end   = now + 3600;

        assertThat(MarketStatusResolver.resolveByPeriod(start, end)).isEqualTo(MarketStatus.OPEN);
    }

    @Test
    @DisplayName("M2: resolveByPeriod — now 가 end 이후이면 CLOSED")
    void m2_nowAfterEndReturnsClosed() {
        long now = Instant.now().getEpochSecond();
        long start = now - 7200;
        long end   = now - 3600;

        assertThat(MarketStatusResolver.resolveByPeriod(start, end)).isEqualTo(MarketStatus.CLOSED);
    }

    @Test
    @DisplayName("M3: resolveByPeriod — now 가 start 이전이면 CLOSED")
    void m3_nowBeforeStartReturnsClosed() {
        long now = Instant.now().getEpochSecond();
        long start = now + 3600;
        long end   = now + 7200;

        assertThat(MarketStatusResolver.resolveByPeriod(start, end)).isEqualTo(MarketStatus.CLOSED);
    }

    @Test
    @DisplayName("M4: resolveByPeriod(0, 0) — fallback 경로로 non-null 상태 반환")
    void m4_zeroPeriodFallsBackToResolve() {
        MarketStatus status = MarketStatusResolver.resolveByPeriod(0, 0);

        assertThat(status).isIn(MarketStatus.OPEN, MarketStatus.CLOSED);
    }

    @Test
    @DisplayName("M5: priceLabel(OPEN, any) → '실시간 (약 1~2분 지연)'")
    void m5_openPriceLabel() {
        OffsetDateTime any = OffsetDateTime.now(ZoneOffset.UTC);

        assertThat(MarketStatusResolver.priceLabel(MarketStatus.OPEN, any))
                .isEqualTo("실시간 (약 1~2분 지연)");
        assertThat(MarketStatusResolver.priceLabel(MarketStatus.OPEN, null))
                .isEqualTo("실시간 (약 1~2분 지연)");
    }

    @Test
    @DisplayName("M6: priceLabel(CLOSED, non-null) → '{M/d} 정규장 종가' (ET 기준)")
    void m6_closedPriceLabelWithDate() {
        // 2026-04-17 20:00:00 UTC = 2026-04-17 16:00 ET
        OffsetDateTime utc = OffsetDateTime.of(2026, 4, 17, 20, 0, 0, 0, ZoneOffset.UTC);

        String label = MarketStatusResolver.priceLabel(MarketStatus.CLOSED, utc);

        assertThat(label).isEqualTo("4/17 정규장 종가");
    }

    @Test
    @DisplayName("M7: priceLabel(CLOSED, null) → '정규장 종가'")
    void m7_closedPriceLabelNullFallback() {
        assertThat(MarketStatusResolver.priceLabel(MarketStatus.CLOSED, null))
                .isEqualTo("정규장 종가");
    }
}
