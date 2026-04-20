package com.aistockadvisor.ai.service;

import com.aistockadvisor.ai.domain.EvaluationWindow;
import com.aistockadvisor.ai.domain.SignalOutcome;
import com.aistockadvisor.ai.domain.SignalOutcomeEvaluator;
import com.aistockadvisor.ai.infra.AiSignalAuditEntity;
import com.aistockadvisor.ai.infra.AiSignalAuditRepository;
import com.aistockadvisor.ai.infra.AiSignalEvaluationEntity;
import com.aistockadvisor.ai.infra.AiSignalEvaluationRepository;
import com.aistockadvisor.stock.infra.CandleEntity;
import com.aistockadvisor.stock.infra.CandleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * AI 시그널 N일 후 방향 정합도 평가 오케스트레이션.
 *
 * <p>평가 원천은 {@code ai_signal_audit} (append-only). 평가 결과는 별도 테이블
 * {@code ai_signal_evaluation} 에 파생 저장. UNIQUE(audit_id, window_days) 로 idempotent.
 *
 * 참조: docs/02-design/features/signal-accuracy.design.md §5, §11.2 Step 3
 */
@Service
public class SignalEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(SignalEvaluationService.class);

    /** NY ET 기준으로 signal 일자·target 일자를 해석 (일봉은 NY 영업일 close 기준). */
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");

    /** target date 가 주말/휴일일 때 앞으로 탐색할 최대 일수. */
    private static final int MAX_FORWARD_BUSINESS_DAY_SEARCH = 7;

    /** signal date 에 캔들이 없을 때 뒤로 탐색할 최대 일수. */
    private static final int MAX_BACKWARD_BUSINESS_DAY_SEARCH = 5;

    private final AiSignalAuditRepository auditRepo;
    private final AiSignalEvaluationRepository evalRepo;
    private final CandleRepository candleRepo;
    private final SignalOutcomeEvaluator evaluator;
    private final int batchSize;
    private final Clock clock;
    private final ApplicationContext ctx;

    @Autowired
    public SignalEvaluationService(AiSignalAuditRepository auditRepo,
                                   AiSignalEvaluationRepository evalRepo,
                                   CandleRepository candleRepo,
                                   @Value("${app.ai.evaluation.flat-threshold-pct:2.0}")
                                   BigDecimal flatThresholdPct,
                                   @Value("${app.ai.evaluation.batch-size:500}")
                                   int batchSize,
                                   ApplicationContext ctx) {
        this(auditRepo, evalRepo, candleRepo,
                new SignalOutcomeEvaluator(flatThresholdPct), batchSize, Clock.systemUTC(), ctx);
    }

    // Test-only ctor
    SignalEvaluationService(AiSignalAuditRepository auditRepo,
                            AiSignalEvaluationRepository evalRepo,
                            CandleRepository candleRepo,
                            SignalOutcomeEvaluator evaluator,
                            int batchSize,
                            Clock clock) {
        this(auditRepo, evalRepo, candleRepo, evaluator, batchSize, clock, null);
    }

    SignalEvaluationService(AiSignalAuditRepository auditRepo,
                            AiSignalEvaluationRepository evalRepo,
                            CandleRepository candleRepo,
                            SignalOutcomeEvaluator evaluator,
                            int batchSize,
                            Clock clock,
                            ApplicationContext ctx) {
        this.auditRepo = auditRepo;
        this.evalRepo = evalRepo;
        this.candleRepo = candleRepo;
        this.evaluator = evaluator;
        this.batchSize = batchSize;
        this.clock = clock;
        this.ctx = ctx;
    }

    /**
     * 하나의 window 에 대해 평가를 실행한다. 한 번에 최대 {@code batchSize} 건 처리.
     * 페이지를 순회하며 전체 미평가 대상을 소진할 때까지 반복한다.
     *
     * @param window 평가 윈도우
     * @param since  이 시각 이후 audit 만 대상 (null 이면 전체)
     * @return 처리 통계
     */
    @Transactional
    public EvaluationStats evaluateWindow(EvaluationWindow window, Instant since) {
        short windowDays = window.asShort();
        Instant maxGeneratedAt = Instant.now(clock)
                .minus(window.days(), ChronoUnit.DAYS);

        int processed = 0;
        int skippedNoPrice = 0;
        int skippedDuplicate = 0;

        while (true) {
            List<AiSignalAuditEntity> batch = auditRepo.findUnevaluated(
                    maxGeneratedAt, since, windowDays,
                    PageRequest.of(0, batchSize));

            if (batch.isEmpty()) break;

            for (AiSignalAuditEntity audit : batch) {
                var result = tryEvaluateOne(audit, window);
                switch (result) {
                    case SAVED -> processed++;
                    case NO_PRICE -> skippedNoPrice++;
                    case DUPLICATE -> skippedDuplicate++;
                }
            }

            if (batch.size() < batchSize) break;
        }

        log.info("signal-evaluation window={} processed={} skippedNoPrice={} skippedDuplicate={}",
                windowDays, processed, skippedNoPrice, skippedDuplicate);
        return new EvaluationStats(windowDays, processed, skippedNoPrice, skippedDuplicate);
    }

    /**
     * 관리자 백필용 비동기 트리거. 응답을 즉시 반환하기 위해 virtual-thread 에서 실행.
     *
     * @param window 평가 윈도우
     * @param since  이 시각 이후 audit 만 대상 (null 이면 전체)
     */
    @Async
    public void evaluateWindowAsync(EvaluationWindow window, Instant since) {
        try {
            // self-proxy 로 호출해 @Transactional 적용 보장 (self-invocation 시 AOP 우회 방지)
            SignalEvaluationService self = (ctx != null)
                    ? ctx.getBean(SignalEvaluationService.class)
                    : this;
            self.evaluateWindow(window, since);
        } catch (Exception ex) {
            log.error("backfill evaluation failed window={} reason={}",
                    window.days(), ex.getMessage(), ex);
        }
    }

    /**
     * 백필 대상 건수 조회 (admin 응답 메타데이터용).
     */
    public long countUnevaluated(EvaluationWindow window, Instant since) {
        Instant maxGeneratedAt = Instant.now(clock)
                .minus(window.days(), ChronoUnit.DAYS);
        return auditRepo.countUnevaluated(maxGeneratedAt, since, window.asShort());
    }

    public int batchSize() {
        return batchSize;
    }

    private OneResult tryEvaluateOne(AiSignalAuditEntity audit, EvaluationWindow window) {
        try {
            if (evalRepo.existsByAuditIdAndWindowDays(audit.getId(), window.asShort())) {
                return OneResult.DUPLICATE;
            }

            LocalDate signalDate = audit.getGeneratedAt().atZone(MARKET_ZONE).toLocalDate();
            LocalDate targetDate = signalDate.plusDays(window.days());

            Optional<BigDecimal> priceAtSignal = resolvePriceAtSignal(audit, signalDate);
            if (priceAtSignal.isEmpty()) return OneResult.NO_PRICE;

            Optional<CandleWithDate> endCandle = resolveEndCandle(audit.getTicker(), targetDate);
            if (endCandle.isEmpty()) return OneResult.NO_PRICE;

            SignalOutcome outcome = evaluator.evaluate(
                    audit.getSignal(),
                    priceAtSignal.get(),
                    endCandle.get().price());

            Instant targetAt = targetDate.atStartOfDay(MARKET_ZONE).toInstant();

            AiSignalEvaluationEntity entity = new AiSignalEvaluationEntity(
                    UUID.randomUUID(),
                    audit.getId(),
                    window.asShort(),
                    audit.getGeneratedAt(),
                    targetAt,
                    priceAtSignal.get(),
                    endCandle.get().price(),
                    endCandle.get().tradeDate(),
                    audit.getSignal(),
                    outcome.predicted(),
                    outcome.actual(),
                    outcome.changePct(),
                    outcome.hit(),
                    Instant.now(clock)
            );

            try {
                evalRepo.save(entity);
                return OneResult.SAVED;
            } catch (DataIntegrityViolationException duplicateRace) {
                return OneResult.DUPLICATE;
            }
        } catch (Exception ex) {
            log.warn("evaluation failed auditId={} reason={}", audit.getId(), ex.getMessage());
            return OneResult.NO_PRICE;
        }
    }

    private Optional<BigDecimal> resolvePriceAtSignal(AiSignalAuditEntity audit, LocalDate signalDate) {
        // 1순위: audit.context_payload.quote.price (시그널 시점 lived price)
        Map<String, Object> ctx = audit.getContextPayload();
        if (ctx != null) {
            Object quote = ctx.get("quote");
            if (quote instanceof Map<?, ?> quoteMap) {
                Object price = quoteMap.get("price");
                if (price instanceof Number num) {
                    BigDecimal bd = BigDecimal.valueOf(num.doubleValue());
                    if (bd.signum() > 0) return Optional.of(bd);
                }
            }
        }

        // 2순위: signalDate 또는 그 이전 거래일의 캔들 close
        return findCandleAtOrBefore(audit.getTicker(), signalDate)
                .map(CandleEntity::getClose);
    }

    private Optional<CandleWithDate> resolveEndCandle(String ticker, LocalDate targetDate) {
        return findCandleAtOrAfter(ticker, targetDate)
                .map(c -> new CandleWithDate(c.getClose(), c.getTradeDate()));
    }

    private Optional<CandleEntity> findCandleAtOrBefore(String ticker, LocalDate date) {
        List<CandleEntity> candles = candleRepo.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(
                ticker, date.minusDays(MAX_BACKWARD_BUSINESS_DAY_SEARCH), date);
        if (candles.isEmpty()) return Optional.empty();
        return Optional.of(candles.get(candles.size() - 1));
    }

    private Optional<CandleEntity> findCandleAtOrAfter(String ticker, LocalDate date) {
        List<CandleEntity> candles = candleRepo.findByTickerAndTradeDateBetweenOrderByTradeDateAsc(
                ticker, date, date.plusDays(MAX_FORWARD_BUSINESS_DAY_SEARCH));
        if (candles.isEmpty()) return Optional.empty();
        return Optional.of(candles.get(0));
    }

    private record CandleWithDate(BigDecimal price, LocalDate tradeDate) {}

    private enum OneResult { SAVED, NO_PRICE, DUPLICATE }

    public record EvaluationStats(int windowDays, int processed, int skippedNoPrice, int skippedDuplicate) {
    }
}
