package com.nowini.ai.domain;

import com.nowini.ai.domain.AiSignal.Signal;
import com.nowini.ai.domain.SignalOutcome.Direction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Pure domain logic: Signal → Direction 매핑, change_pct → Direction 판정, hit 계산.
 * 외부 의존 0. 단위 테스트로 경계 완전 커버.
 *
 * 참조: docs/02-design/features/signal-accuracy.design.md §3.1, §11.2 Step 2
 */
public final class SignalOutcomeEvaluator {

    private static final MathContext PCT_CTX = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BigDecimal flatThresholdPct;

    /**
     * @param flatThresholdPct 실제 변동률 |change_pct| <= 이 값이면 FLAT 으로 간주. inclusive.
     *                         Neutral 시그널은 이 범위 내 움직임을 hit 으로 본다.
     */
    public SignalOutcomeEvaluator(BigDecimal flatThresholdPct) {
        if (flatThresholdPct == null || flatThresholdPct.signum() < 0) {
            throw new IllegalArgumentException("flatThresholdPct must be >= 0");
        }
        this.flatThresholdPct = flatThresholdPct;
    }

    /**
     * Signal → Direction 매핑.
     * STRONG_BUY/BUY → UP, NEUTRAL → FLAT, SELL/STRONG_SELL → DOWN.
     */
    public static Direction mapSignalToDirection(Signal signal) {
        return switch (signal) {
            case STRONG_BUY, BUY -> Direction.UP;
            case NEUTRAL -> Direction.FLAT;
            case SELL, STRONG_SELL -> Direction.DOWN;
        };
    }

    /**
     * change_pct 를 Direction 으로 판정. |change_pct| <= flatThresholdPct 이면 FLAT.
     */
    public Direction classifyActual(BigDecimal changePct) {
        if (changePct == null) throw new IllegalArgumentException("changePct must not be null");
        BigDecimal abs = changePct.abs();
        if (abs.compareTo(flatThresholdPct) <= 0) return Direction.FLAT;
        return changePct.signum() > 0 ? Direction.UP : Direction.DOWN;
    }

    /**
     * 시점 가격과 종료 가격으로부터 SignalOutcome 계산.
     *
     * @param signal        원 시그널 (5-class)
     * @param priceAtSignal 시그널 시점 가격 (> 0)
     * @param priceAtEnd    window 경과 후 종가 (> 0)
     */
    public SignalOutcome evaluate(Signal signal, BigDecimal priceAtSignal, BigDecimal priceAtEnd) {
        if (priceAtSignal == null || priceAtSignal.signum() <= 0) {
            throw new IllegalArgumentException("priceAtSignal must be > 0");
        }
        if (priceAtEnd == null || priceAtEnd.signum() <= 0) {
            throw new IllegalArgumentException("priceAtEnd must be > 0");
        }
        BigDecimal changePct = priceAtEnd.subtract(priceAtSignal)
                .divide(priceAtSignal, PCT_CTX)
                .multiply(HUNDRED)
                .setScale(4, RoundingMode.HALF_UP);

        Direction predicted = mapSignalToDirection(signal);
        Direction actual = classifyActual(changePct);
        boolean hit = predicted == actual;
        return new SignalOutcome(predicted, actual, changePct, hit);
    }
}
