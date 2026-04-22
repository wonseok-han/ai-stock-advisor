package com.aistockadvisor.ai.domain;

import com.aistockadvisor.ai.domain.AiSignal.Signal;
import com.aistockadvisor.ai.domain.SignalOutcome.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 참조: docs/02-design/features/signal-accuracy.design.md §8.2
 */
class SignalOutcomeEvaluatorTest {

    private final SignalOutcomeEvaluator evaluator =
            new SignalOutcomeEvaluator(new BigDecimal("2.0"));

    @Test
    void mapSignalToDirection_covers_all_enum_values() {
        assertThat(SignalOutcomeEvaluator.mapSignalToDirection(Signal.STRONG_BUY)).isEqualTo(Direction.UP);
        assertThat(SignalOutcomeEvaluator.mapSignalToDirection(Signal.BUY)).isEqualTo(Direction.UP);
        assertThat(SignalOutcomeEvaluator.mapSignalToDirection(Signal.NEUTRAL)).isEqualTo(Direction.FLAT);
        assertThat(SignalOutcomeEvaluator.mapSignalToDirection(Signal.SELL)).isEqualTo(Direction.DOWN);
        assertThat(SignalOutcomeEvaluator.mapSignalToDirection(Signal.STRONG_SELL)).isEqualTo(Direction.DOWN);
    }

    @ParameterizedTest(name = "{0}% → {1}")
    @CsvSource({
            "5.0,   UP",
            "2.01,  UP",
            "2.0,   FLAT",
            "1.99,  FLAT",
            "0.0,   FLAT",
            "-1.99, FLAT",
            "-2.0,  FLAT",
            "-2.01, DOWN",
            "-5.0,  DOWN"
    })
    void classifyActual_boundary(String changePct, Direction expected) {
        assertThat(evaluator.classifyActual(new BigDecimal(changePct))).isEqualTo(expected);
    }

    @Test
    void buy_with_plus_3pct_hits() {
        SignalOutcome o = evaluator.evaluate(Signal.BUY,
                new BigDecimal("100.00"), new BigDecimal("103.00"));
        assertThat(o.predicted()).isEqualTo(Direction.UP);
        assertThat(o.actual()).isEqualTo(Direction.UP);
        assertThat(o.hit()).isTrue();
        assertThat(o.changePct()).isEqualByComparingTo("3.0000");
    }

    @Test
    void buy_with_minus_half_pct_misses_predicted_up_actual_flat() {
        SignalOutcome o = evaluator.evaluate(Signal.BUY,
                new BigDecimal("100.00"), new BigDecimal("99.50"));
        assertThat(o.predicted()).isEqualTo(Direction.UP);
        assertThat(o.actual()).isEqualTo(Direction.FLAT);
        assertThat(o.hit()).isFalse();
    }

    @Test
    void neutral_with_plus_one_pct_hits() {
        SignalOutcome o = evaluator.evaluate(Signal.NEUTRAL,
                new BigDecimal("100.00"), new BigDecimal("101.00"));
        assertThat(o.predicted()).isEqualTo(Direction.FLAT);
        assertThat(o.actual()).isEqualTo(Direction.FLAT);
        assertThat(o.hit()).isTrue();
    }

    @Test
    void neutral_with_plus_3pct_misses() {
        SignalOutcome o = evaluator.evaluate(Signal.NEUTRAL,
                new BigDecimal("100.00"), new BigDecimal("103.00"));
        assertThat(o.predicted()).isEqualTo(Direction.FLAT);
        assertThat(o.actual()).isEqualTo(Direction.UP);
        assertThat(o.hit()).isFalse();
    }

    @Test
    void strong_sell_with_minus_5pct_hits() {
        SignalOutcome o = evaluator.evaluate(Signal.STRONG_SELL,
                new BigDecimal("100.00"), new BigDecimal("95.00"));
        assertThat(o.predicted()).isEqualTo(Direction.DOWN);
        assertThat(o.actual()).isEqualTo(Direction.DOWN);
        assertThat(o.hit()).isTrue();
        assertThat(o.changePct()).isEqualByComparingTo("-5.0000");
    }

    @Test
    void boundary_exactly_at_flat_threshold_is_flat_inclusive() {
        SignalOutcome o = evaluator.evaluate(Signal.BUY,
                new BigDecimal("100.00"), new BigDecimal("102.00"));
        assertThat(o.actual()).isEqualTo(Direction.FLAT);
        assertThat(o.hit()).isFalse();
    }

    @Test
    void negative_price_throws() {
        assertThatThrownBy(() -> evaluator.evaluate(Signal.BUY,
                new BigDecimal("-1"), new BigDecimal("100")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluate(Signal.BUY,
                new BigDecimal("100"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negative_threshold_throws() {
        assertThatThrownBy(() -> new SignalOutcomeEvaluator(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
