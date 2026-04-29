package com.nowini.ai.service;

import com.nowini.ai.domain.AccuracySummary;
import com.nowini.ai.domain.AccuracySummary.BucketStat;
import com.nowini.ai.domain.AiSignal.Signal;
import com.nowini.ai.domain.EvaluationWindow;
import com.nowini.ai.infra.AiSignalEvaluationRepository;
import com.nowini.ai.infra.AiSignalEvaluationRepository.SignalBucket;
import com.nowini.ai.infra.AiSignalEvaluationRepository.TotalAndHits;
import com.nowini.cache.RedisCacheAdapter;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 전역 정합도 집계 서비스. Redis 1시간 캐시 적용.
 *
 * 참조: docs/02-design/features/signal-accuracy.design.md §5, §11.2 Step 5
 */
@Service
public class SignalAccuracyService {

    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final String KEY_PREFIX = "ai:accuracy:w";

    private final AiSignalEvaluationRepository repo;
    private final RedisCacheAdapter cache;

    public SignalAccuracyService(AiSignalEvaluationRepository repo, RedisCacheAdapter cache) {
        this.repo = repo;
        this.cache = cache;
    }

    public AccuracySummary summarize(EvaluationWindow window) {
        String key = KEY_PREFIX + window.days();
        return cache.getOrLoad(key, new TypeReference<AccuracySummary>() {},
                CACHE_TTL, () -> loadFromDb(window));
    }

    @Transactional(readOnly = true)
    AccuracySummary loadFromDb(EvaluationWindow window) {
        short windowDays = window.asShort();
        TotalAndHits agg = repo.aggregateTotal(windowDays);

        int sampleSize = agg == null || agg.getTotal() == null ? 0 : agg.getTotal().intValue();
        Instant evaluatedThrough = repo.findLatestEvaluatedAt(windowDays);

        if (sampleSize < AccuracySummary.MIN_SAMPLE_SIZE) {
            return new AccuracySummary(
                    window.days(), sampleSize, false, null, Map.of(),
                    evaluatedThrough, "평가 누적 중",
                    AccuracySummary.STANDARD_DISCLAIMER);
        }

        long hits = agg.getHits() == null ? 0L : agg.getHits();
        BigDecimal hitRate = ratio(hits, sampleSize);

        List<SignalBucket> buckets = repo.aggregateBySignal(windowDays);
        Map<Signal, BucketStat> bySignal = new EnumMap<>(Signal.class);
        for (SignalBucket b : buckets) {
            long count = b.getTotal() == null ? 0L : b.getTotal();
            long bucketHits = b.getHits() == null ? 0L : b.getHits();
            bySignal.put(b.getSignal(), new BucketStat((int) count, ratio(bucketHits, count)));
        }

        return new AccuracySummary(
                window.days(), sampleSize, true, hitRate, bySignal,
                evaluatedThrough, null, AccuracySummary.STANDARD_DISCLAIMER);
    }

    private static BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }
}
