package com.aistockadvisor.stock.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

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
 *   <li>M8: durationUntilNextOpen — 정규장 중 → Duration.ZERO</li>
 *   <li>M9: durationUntilNextOpen — 평일 장 마감 후 → 다음 날 09:30 ET까지</li>
 *   <li>M10: durationUntilNextOpen — 평일 장 개시 전 → 당일 09:30 ET까지</li>
 *   <li>M11: durationUntilNextOpen — 금요일 장 마감 후 → 월요일 09:30 ET까지</li>
 *   <li>M12: durationUntilNextOpen — 토요일 → 월요일 09:30 ET까지</li>
 *   <li>M13: durationUntilNextOpen — 일요일 → 월요일 09:30 ET까지</li>
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

        assertThat(label).startsWith("4/17 정규장 종가");
    }

    @Test
    @DisplayName("M7: priceLabel(CLOSED, null) → '정규장 종가'")
    void m7_closedPriceLabelNullFallback() {
        assertThat(MarketStatusResolver.priceLabel(MarketStatus.CLOSED, null))
                .startsWith("정규장 종가");
    }

    // --- durationUntilNextOpen 테스트 (M8~M13) ---

    private static final ZoneId ET = ZoneId.of("America/New_York");

    @Test
    @DisplayName("M8: durationUntilNextOpen — 정규장 중 → Duration.ZERO")
    void m8_duringMarketHoursReturnsZero() {
        // 2026-04-22 (수) 14:00 ET
        ZonedDateTime wed1400 = ZonedDateTime.of(
                LocalDate.of(2026, 4, 22), LocalTime.of(14, 0), ET);

        assertThat(MarketStatusResolver.durationUntilNextOpen(wed1400)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("M9: durationUntilNextOpen — 평일 장 마감 후 → 다음 날 09:30까지")
    void m9_weekdayAfterCloseUntilNextDay() {
        // 2026-04-22 (수) 18:00 ET → 목 09:30 ET = 15.5시간
        ZonedDateTime wed1800 = ZonedDateTime.of(
                LocalDate.of(2026, 4, 22), LocalTime.of(18, 0), ET);

        Duration d = MarketStatusResolver.durationUntilNextOpen(wed1800);
        assertThat(d).isPositive();
        assertThat(d.toMinutes()).isEqualTo(15 * 60 + 30); // 15시간 30분
    }

    @Test
    @DisplayName("M10: durationUntilNextOpen — 평일 장 개시 전 → 당일 09:30까지")
    void m10_weekdayBeforeOpenUntilSameDay() {
        // 2026-04-23 (목) 07:00 ET → 목 09:30 ET = 2.5시간
        ZonedDateTime thu0700 = ZonedDateTime.of(
                LocalDate.of(2026, 4, 23), LocalTime.of(7, 0), ET);

        Duration d = MarketStatusResolver.durationUntilNextOpen(thu0700);
        assertThat(d).isPositive();
        assertThat(d.toMinutes()).isEqualTo(2 * 60 + 30); // 2시간 30분
    }

    @Test
    @DisplayName("M11: durationUntilNextOpen — 금요일 장 마감 후 → 월요일 09:30까지")
    void m11_fridayAfterCloseUntilMonday() {
        // 2026-04-24 (금) 18:00 ET → 월 09:30 ET = 63.5시간
        ZonedDateTime fri1800 = ZonedDateTime.of(
                LocalDate.of(2026, 4, 24), LocalTime.of(18, 0), ET);

        Duration d = MarketStatusResolver.durationUntilNextOpen(fri1800);
        assertThat(d.toMinutes()).isEqualTo(63 * 60 + 30); // 63시간 30분
    }

    @Test
    @DisplayName("M12: durationUntilNextOpen — 토요일 → 월요일 09:30까지")
    void m12_saturdayUntilMonday() {
        // 2026-04-25 (토) 12:00 ET → 월 09:30 ET = 45.5시간
        ZonedDateTime sat1200 = ZonedDateTime.of(
                LocalDate.of(2026, 4, 25), LocalTime.of(12, 0), ET);

        Duration d = MarketStatusResolver.durationUntilNextOpen(sat1200);
        assertThat(d.toMinutes()).isEqualTo(45 * 60 + 30); // 45시간 30분
    }

    @Test
    @DisplayName("M13: durationUntilNextOpen — 일요일 → 월요일 09:30까지")
    void m13_sundayUntilMonday() {
        // 2026-04-26 (일) 12:00 ET → 월 09:30 ET = 21.5시간
        ZonedDateTime sun1200 = ZonedDateTime.of(
                LocalDate.of(2026, 4, 26), LocalTime.of(12, 0), ET);

        Duration d = MarketStatusResolver.durationUntilNextOpen(sun1200);
        assertThat(d.toMinutes()).isEqualTo(21 * 60 + 30); // 21시간 30분
    }
}
