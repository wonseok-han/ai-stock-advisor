package com.aistockadvisor.stock.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class MarketStatusResolver {

    private static final ZoneId ET = ZoneId.of("America/New_York");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime OPEN  = LocalTime.of(9, 30);
    private static final LocalTime CLOSE = LocalTime.of(16, 0);
    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private MarketStatusResolver() {}

    public static MarketStatus resolve() {
        return resolve(ZonedDateTime.now(ET));
    }

    static MarketStatus resolve(ZonedDateTime now) {
        DayOfWeek dow = now.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return MarketStatus.CLOSED;
        }
        LocalTime t = now.toLocalTime();
        return (t.compareTo(OPEN) >= 0 && t.compareTo(CLOSE) < 0)
                ? MarketStatus.OPEN : MarketStatus.CLOSED;
    }

    public static String priceLabel(MarketStatus status, OffsetDateTime updatedAt) {
        if (status == MarketStatus.OPEN) {
            return "실시간 (약 1~2분 지연)";
        }
        ZonedDateTime closeEt = ZonedDateTime.now(ET).with(CLOSE);
        String closeKst = closeEt.withZoneSameInstant(KST).format(TIME_FMT);
        if (updatedAt != null) {
            String date = updatedAt.atZoneSameInstant(ET).format(LABEL_FMT);
            return date + " 정규장 종가 (KST " + closeKst + ")";
        }
        return "정규장 종가 (KST " + closeKst + ")";
    }

    public static Duration durationUntilNextOpen() {
        return durationUntilNextOpen(ZonedDateTime.now(ET));
    }

    static Duration durationUntilNextOpen(ZonedDateTime now) {
        if (resolve(now) == MarketStatus.OPEN) {
            return Duration.ZERO;
        }
        return Duration.between(now, nextOpenTime(now));
    }

    private static ZonedDateTime nextOpenTime(ZonedDateTime now) {
        ZonedDateTime today930 = now.toLocalDate().atTime(OPEN).atZone(ET);

        if (now.isBefore(today930) && isWeekday(now.getDayOfWeek())) {
            return today930;
        }

        ZonedDateTime next = today930.plusDays(1);
        while (!isWeekday(next.getDayOfWeek())) {
            next = next.plusDays(1);
        }
        return next;
    }

    private static boolean isWeekday(DayOfWeek dow) {
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    /** Yahoo currentTradingPeriod.regular 로 판단 (epoch 초 기준). */
    public static MarketStatus resolveByPeriod(long regularStart, long regularEnd) {
        if (regularStart <= 0 || regularEnd <= 0) return resolve();
        long now = Instant.now().getEpochSecond();
        return (now >= regularStart && now < regularEnd)
                ? MarketStatus.OPEN : MarketStatus.CLOSED;
    }
}
