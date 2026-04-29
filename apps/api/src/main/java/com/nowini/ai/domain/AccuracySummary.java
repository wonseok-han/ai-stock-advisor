package com.nowini.ai.domain;

import com.nowini.ai.domain.AiSignal.Signal;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * 전역 시그널 정합도 집계 응답.
 *
 * <p>샘플 수가 {@code MIN_SAMPLE_SIZE} 미만이면 {@code sampleSizeSufficient=false},
 * {@code hitRate=null}, {@code bySignal={}} 로 응답 (FE 에서 배지 숨김).
 *
 * 참조: docs/02-design/features/signal-accuracy.design.md §3.1, §4.2
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccuracySummary(
        int window,
        int sampleSize,
        boolean sampleSizeSufficient,
        BigDecimal hitRate,
        Map<Signal, BucketStat> bySignal,
        Instant evaluatedThrough,
        String message,
        String disclaimer
) {

    public static final int MIN_SAMPLE_SIZE = 5;

    public static final String STANDARD_DISCLAIMER =
            "과거 성과는 미래 수익을 보장하지 않습니다. 본 정합도는 내부 튜닝 지표이며 투자 판단 근거가 아닙니다.";

    public record BucketStat(int count, BigDecimal hitRate) {}
}
