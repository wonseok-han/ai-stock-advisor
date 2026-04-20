package com.aistockadvisor.notification.service;

import com.aistockadvisor.notification.service.NotificationDedupPolicy.Action;
import com.aistockadvisor.notification.service.NotificationDedupPolicy.Decision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDedupPolicyTest {

    private static final BigDecimal THRESHOLD_5 = new BigDecimal("5");
    private static final BigDecimal RESET_RATIO = new BigDecimal("0.6");
    private static final Duration COOLDOWN_4H = Duration.ofHours(4);
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 4, 20, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    @DisplayName("T1: 임계값 미달 & 이전 상태 없음 -> NOOP")
    void t1_belowThreshold_noPriorState() {
        Decision d = NotificationDedupPolicy.decide(
                new BigDecimal("3"), THRESHOLD_5, RESET_RATIO, COOLDOWN_4H,
                false, null, NOW);
        assertThat(d.action()).isEqualTo(Action.NOOP);
    }

    @Test
    @DisplayName("T2: 최초 임계값 돌파 -> SEND")
    void t2_firstTransitionAbove() {
        Decision d = NotificationDedupPolicy.decide(
                new BigDecimal("5"), THRESHOLD_5, RESET_RATIO, COOLDOWN_4H,
                false, null, NOW);
        assertThat(d.action()).isEqualTo(Action.SEND);
    }

    @Test
    @DisplayName("T3: 임계값 초과 유지 -> SKIP_NO_TRANSITION")
    void t3_sustainedAbove() {
        Decision d = NotificationDedupPolicy.decide(
                new BigDecimal("5.3"), THRESHOLD_5, RESET_RATIO, COOLDOWN_4H,
                true, NOW.minusMinutes(30), NOW);
        assertThat(d.action()).isEqualTo(Action.SKIP_NO_TRANSITION);
    }

    @Test
    @DisplayName("T4: 리셋 구간 이전 진동(4.9% vs 5%) -> NOOP (stay above)")
    void t4_flappingBetweenResetAndTrigger() {
        Decision d = NotificationDedupPolicy.decide(
                new BigDecimal("4.9"), THRESHOLD_5, RESET_RATIO, COOLDOWN_4H,
                true, NOW.minusMinutes(30), NOW);
        assertThat(d.action()).isEqualTo(Action.NOOP);
    }

    @Test
    @DisplayName("T5: 리셋 임계값(3%) 아래로 하락 -> RESET_ONLY")
    void t5_droppedBelowReset() {
        Decision d = NotificationDedupPolicy.decide(
                new BigDecimal("2.5"), THRESHOLD_5, RESET_RATIO, COOLDOWN_4H,
                true, NOW.minusHours(1), NOW);
        assertThat(d.action()).isEqualTo(Action.RESET_ONLY);
    }

    @Test
    @DisplayName("T6: 리셋 후 재돌파 but 쿨다운 중 -> SKIP_COOLDOWN")
    void t6_retriggerWithinCooldown() {
        Decision d = NotificationDedupPolicy.decide(
                new BigDecimal("5.1"), THRESHOLD_5, RESET_RATIO, COOLDOWN_4H,
                false, NOW.minusHours(1), NOW);
        assertThat(d.action()).isEqualTo(Action.SKIP_COOLDOWN);
    }

    @Test
    @DisplayName("T7: 리셋 후 재돌파 & 쿨다운 만료 -> SEND")
    void t7_retriggerAfterCooldown() {
        Decision d = NotificationDedupPolicy.decide(
                new BigDecimal("5.1"), THRESHOLD_5, RESET_RATIO, COOLDOWN_4H,
                false, NOW.minusHours(5), NOW);
        assertThat(d.action()).isEqualTo(Action.SEND);
    }

    @Test
    @DisplayName("T8: 시계 역전(lastNotifiedAt 이 미래) -> SKIP_COOLDOWN (fail-safe)")
    void t8_clockSkew() {
        Decision d = NotificationDedupPolicy.decide(
                new BigDecimal("7"), THRESHOLD_5, RESET_RATIO, COOLDOWN_4H,
                false, NOW.plusHours(1), NOW);
        assertThat(d.action()).isEqualTo(Action.SKIP_COOLDOWN);
    }

    @Test
    @DisplayName("T9: 정확히 리셋 임계값(3%) -> NOOP (< 엄격 비교)")
    void t9_exactlyAtResetThreshold() {
        Decision d = NotificationDedupPolicy.decide(
                new BigDecimal("3"), THRESHOLD_5, RESET_RATIO, COOLDOWN_4H,
                true, NOW.minusHours(1), NOW);
        assertThat(d.action()).isEqualTo(Action.NOOP);
    }
}
