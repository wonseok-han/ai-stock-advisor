package com.aistockadvisor.ai.web;

import com.aistockadvisor.TestcontainersConfiguration;
import com.aistockadvisor.ai.domain.AiSignal.Signal;
import com.aistockadvisor.ai.domain.AiSignal.Timeframe;
import com.aistockadvisor.ai.domain.SignalOutcome.Direction;
import com.aistockadvisor.ai.infra.AiSignalAuditEntity;
import com.aistockadvisor.ai.infra.AiSignalAuditRepository;
import com.aistockadvisor.ai.infra.AiSignalEvaluationEntity;
import com.aistockadvisor.ai.infra.AiSignalEvaluationRepository;
import com.aistockadvisor.cache.RedisCacheAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 참조: docs/02-design/features/signal-accuracy.design.md §4.2, §8.2
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SignalAccuracyControllerIT {

    @Autowired MockMvc mvc;
    @Autowired AiSignalAuditRepository auditRepo;
    @Autowired AiSignalEvaluationRepository evalRepo;
    @Autowired RedisCacheAdapter cache;

    @BeforeEach
    void clean() {
        evalRepo.deleteAll();
        auditRepo.deleteAll();
        cache.evict("ai:accuracy:w7");
        cache.evict("ai:accuracy:w30");
    }

    @Test
    void returns_400_for_invalid_window() throws Exception {
        mvc.perform(get("/api/v1/ai/accuracy").param("window", "14"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defaults_to_window_30() throws Exception {
        mvc.perform(get("/api/v1/ai/accuracy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window").value(30))
                .andExpect(jsonPath("$.sampleSizeSufficient").value(false));
    }

    @Test
    void insufficient_sample_hides_hit_rate() throws Exception {
        // 3건 (< 5) 평가 저장
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            UUID auditId = saveAudit("TSTA" + i, Signal.BUY, now.minus(10, ChronoUnit.DAYS));
            saveEval(auditId, (short) 7, Signal.BUY, Direction.UP, Direction.UP, true, now);
        }

        mvc.perform(get("/api/v1/ai/accuracy").param("window", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window").value(7))
                .andExpect(jsonPath("$.sampleSize").value(3))
                .andExpect(jsonPath("$.sampleSizeSufficient").value(false))
                .andExpect(jsonPath("$.hitRate").doesNotExist());
    }

    @Test
    void sufficient_sample_returns_aggregates() throws Exception {
        Instant now = Instant.now();
        // 5건: BUY 3건 (3 hit), NEUTRAL 2건 (1 hit) = total 4/5 hit
        for (int i = 0; i < 3; i++) {
            UUID auditId = saveAudit("TSTB" + i, Signal.BUY, now.minus(10, ChronoUnit.DAYS));
            saveEval(auditId, (short) 7, Signal.BUY, Direction.UP, Direction.UP, true, now);
        }
        UUID n1 = saveAudit("TSTN1", Signal.NEUTRAL, now.minus(10, ChronoUnit.DAYS));
        saveEval(n1, (short) 7, Signal.NEUTRAL, Direction.FLAT, Direction.FLAT, true, now);
        UUID n2 = saveAudit("TSTN2", Signal.NEUTRAL, now.minus(10, ChronoUnit.DAYS));
        saveEval(n2, (short) 7, Signal.NEUTRAL, Direction.FLAT, Direction.UP, false, now);

        mvc.perform(get("/api/v1/ai/accuracy").param("window", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window").value(7))
                .andExpect(jsonPath("$.sampleSize").value(5))
                .andExpect(jsonPath("$.sampleSizeSufficient").value(true))
                .andExpect(jsonPath("$.hitRate").value(0.8))
                .andExpect(jsonPath("$.bySignal.BUY.count").value(3))
                .andExpect(jsonPath("$.bySignal.BUY.hitRate").value(1.0))
                .andExpect(jsonPath("$.bySignal.NEUTRAL.count").value(2))
                .andExpect(jsonPath("$.bySignal.NEUTRAL.hitRate").value(0.5))
                .andExpect(jsonPath("$.disclaimer").isNotEmpty());
    }

    private UUID saveAudit(String ticker, Signal signal, Instant generatedAt) {
        UUID id = UUID.randomUUID();
        auditRepo.save(new AiSignalAuditEntity(
                id, ticker, UUID.randomUUID(), signal,
                new BigDecimal("0.60"), Timeframe.SHORT,
                List.of("r"), List.of("rk"), "s", "gemini",
                Map.of("quote", Map.of("price", 100.0)),
                null, null, false, 100, 100, 50,
                generatedAt));
        return id;
    }

    private void saveEval(UUID auditId, short window, Signal signal,
                          Direction predicted, Direction actual, boolean hit, Instant evaluatedAt) {
        Instant targetAt = evaluatedAt;
        evalRepo.save(new AiSignalEvaluationEntity(
                UUID.randomUUID(), auditId, window,
                evaluatedAt.minus(window, ChronoUnit.DAYS), targetAt,
                new BigDecimal("100.0000"), new BigDecimal("102.0000"),
                java.time.LocalDate.now(), signal, predicted, actual,
                new BigDecimal("2.0000"), hit, evaluatedAt));
    }
}
