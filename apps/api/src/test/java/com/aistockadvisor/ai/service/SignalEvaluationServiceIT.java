package com.aistockadvisor.ai.service;

import com.aistockadvisor.TestcontainersConfiguration;
import com.aistockadvisor.ai.domain.AiSignal.Signal;
import com.aistockadvisor.ai.domain.AiSignal.Timeframe;
import com.aistockadvisor.ai.domain.EvaluationWindow;
import com.aistockadvisor.ai.infra.AiSignalAuditEntity;
import com.aistockadvisor.ai.infra.AiSignalAuditRepository;
import com.aistockadvisor.ai.infra.AiSignalEvaluationEntity;
import com.aistockadvisor.ai.infra.AiSignalEvaluationRepository;
import com.aistockadvisor.stock.infra.CandleEntity;
import com.aistockadvisor.stock.infra.CandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 참조: docs/02-design/features/signal-accuracy.design.md §8.2
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SignalEvaluationServiceIT {

    @Autowired AiSignalAuditRepository auditRepo;
    @Autowired AiSignalEvaluationRepository evalRepo;
    @Autowired CandleRepository candleRepo;
    @Autowired SignalEvaluationService service;

    @BeforeEach
    void clean() {
        evalRepo.deleteAll();
        auditRepo.deleteAll();
        candleRepo.deleteAll();
    }

    @Test
    void evaluates_buy_signal_with_price_up_as_hit() {
        // signal: BUY @ $100, 7일 후 $105 → UP, hit
        String ticker = "TESTBUY";
        LocalDate signalDate = LocalDate.of(2026, 3, 1);
        Instant generatedAt = signalDate.atStartOfDay(java.time.ZoneId.of("America/New_York")).toInstant();

        saveAudit(ticker, Signal.BUY, generatedAt, 100.0);
        saveCandle(ticker, signalDate, 100.0);
        saveCandle(ticker, signalDate.plusDays(7), 105.0);

        var stats = service.evaluateWindow(EvaluationWindow.W7, null);

        assertThat(stats.processed()).isEqualTo(1);
        List<AiSignalEvaluationEntity> all = evalRepo.findAll();
        assertThat(all).hasSize(1);
        AiSignalEvaluationEntity e = all.get(0);
        assertThat(e.isHit()).isTrue();
        assertThat(e.getWindowDays()).isEqualTo((short) 7);
        assertThat(e.getChangePct()).isEqualByComparingTo("5.0000");
    }

    @Test
    void neutral_signal_with_small_move_is_hit() {
        String ticker = "TESTNEU";
        LocalDate signalDate = LocalDate.of(2026, 3, 1);
        Instant generatedAt = signalDate.atStartOfDay(java.time.ZoneId.of("America/New_York")).toInstant();

        saveAudit(ticker, Signal.NEUTRAL, generatedAt, 100.0);
        saveCandle(ticker, signalDate, 100.0);
        saveCandle(ticker, signalDate.plusDays(7), 100.8);

        service.evaluateWindow(EvaluationWindow.W7, null);

        assertThat(evalRepo.findAll().get(0).isHit()).isTrue();
    }

    @Test
    void idempotent_second_run_does_not_duplicate() {
        String ticker = "TESTIDEM";
        LocalDate signalDate = LocalDate.of(2026, 3, 1);
        Instant generatedAt = signalDate.atStartOfDay(java.time.ZoneId.of("America/New_York")).toInstant();

        saveAudit(ticker, Signal.BUY, generatedAt, 100.0);
        saveCandle(ticker, signalDate, 100.0);
        saveCandle(ticker, signalDate.plusDays(7), 105.0);

        service.evaluateWindow(EvaluationWindow.W7, null);
        var second = service.evaluateWindow(EvaluationWindow.W7, null);

        assertThat(second.processed()).isEqualTo(0);
        assertThat(evalRepo.findAll()).hasSize(1);
    }

    @Test
    void skips_when_end_candle_missing() {
        String ticker = "TESTNOEND";
        LocalDate signalDate = LocalDate.of(2026, 3, 1);
        Instant generatedAt = signalDate.atStartOfDay(java.time.ZoneId.of("America/New_York")).toInstant();

        saveAudit(ticker, Signal.BUY, generatedAt, 100.0);
        saveCandle(ticker, signalDate, 100.0);
        // no end candle

        var stats = service.evaluateWindow(EvaluationWindow.W7, null);
        assertThat(stats.processed()).isEqualTo(0);
        assertThat(stats.skippedNoPrice()).isEqualTo(1);
        assertThat(evalRepo.findAll()).isEmpty();
    }

    @Test
    void skips_fallback_audits() {
        String ticker = "TESTFB";
        LocalDate signalDate = LocalDate.of(2026, 3, 1);
        Instant generatedAt = signalDate.atStartOfDay(java.time.ZoneId.of("America/New_York")).toInstant();

        saveAuditFallback(ticker, generatedAt);
        saveCandle(ticker, signalDate, 100.0);
        saveCandle(ticker, signalDate.plusDays(7), 110.0);

        var stats = service.evaluateWindow(EvaluationWindow.W7, null);
        assertThat(stats.processed()).isEqualTo(0);
    }

    @Test
    void skips_too_recent_audits() {
        String ticker = "TESTRECENT";
        // 어제 생성된 audit → 7일 경과 안 함 → 평가 대상 아님
        Instant generatedAt = Instant.now().minus(1, ChronoUnit.DAYS);

        saveAudit(ticker, Signal.BUY, generatedAt, 100.0);

        var stats = service.evaluateWindow(EvaluationWindow.W7, null);
        assertThat(stats.processed()).isEqualTo(0);
    }

    private void saveAudit(String ticker, Signal signal, Instant generatedAt, double price) {
        AiSignalAuditEntity audit = new AiSignalAuditEntity(
                UUID.randomUUID(), ticker, UUID.randomUUID(),
                signal, new BigDecimal("0.70"), Timeframe.SHORT,
                List.of("test rationale"), List.of("test risk"),
                "test summary",
                "gemini-2.5-flash",
                Map.of("quote", Map.of("price", price)),
                null, null, null, false, 100, 100, 50,
                generatedAt
        );
        auditRepo.save(audit);
    }

    private void saveAuditFallback(String ticker, Instant generatedAt) {
        AiSignalAuditEntity audit = new AiSignalAuditEntity(
                UUID.randomUUID(), ticker, UUID.randomUUID(),
                Signal.NEUTRAL, new BigDecimal("0.50"), Timeframe.MID,
                List.of("fb"), List.of("fb"), "fb",
                "gemini-2.5-flash",
                Map.of(), null, null, null, true, 0, null, null,
                generatedAt
        );
        auditRepo.save(audit);
    }

    private void saveCandle(String ticker, LocalDate date, double close) {
        candleRepo.save(new CandleEntity(ticker, date,
                BigDecimal.valueOf(close), BigDecimal.valueOf(close + 1),
                BigDecimal.valueOf(close - 1), BigDecimal.valueOf(close),
                BigDecimal.valueOf(close), 1_000_000L));
    }
}
