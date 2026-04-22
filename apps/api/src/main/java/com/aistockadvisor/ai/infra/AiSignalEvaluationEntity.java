package com.aistockadvisor.ai.infra;

import com.aistockadvisor.ai.domain.AiSignal.Signal;
import com.aistockadvisor.ai.domain.SignalOutcome.Direction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * ai_signal_evaluation 테이블 매핑 (Flyway V15).
 * 참조: docs/02-design/features/signal-accuracy.design.md §3.3
 */
@Entity
@Table(name = "ai_signal_evaluation")
public class AiSignalEvaluationEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "audit_id", nullable = false)
    private UUID auditId;

    @Column(name = "window_days", nullable = false)
    private short windowDays;

    @Column(name = "signal_generated_at", nullable = false)
    private Instant signalGeneratedAt;

    @Column(name = "evaluation_target_at", nullable = false)
    private Instant evaluationTargetAt;

    @Column(name = "price_at_signal", nullable = false, precision = 12, scale = 4)
    private BigDecimal priceAtSignal;

    @Column(name = "price_at_window_end", nullable = false, precision = 12, scale = 4)
    private BigDecimal priceAtWindowEnd;

    @Column(name = "end_trade_date", nullable = false)
    private LocalDate endTradeDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal", nullable = false, length = 16)
    private Signal signal;

    @Enumerated(EnumType.STRING)
    @Column(name = "predicted_direction", nullable = false, length = 8)
    private Direction predictedDirection;

    @Enumerated(EnumType.STRING)
    @Column(name = "actual_direction", nullable = false, length = 8)
    private Direction actualDirection;

    @Column(name = "change_pct", nullable = false, precision = 8, scale = 4)
    private BigDecimal changePct;

    @Column(name = "hit", nullable = false)
    private boolean hit;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected AiSignalEvaluationEntity() {
    }

    public AiSignalEvaluationEntity(UUID id, UUID auditId, short windowDays,
                                    Instant signalGeneratedAt, Instant evaluationTargetAt,
                                    BigDecimal priceAtSignal, BigDecimal priceAtWindowEnd,
                                    LocalDate endTradeDate, Signal signal,
                                    Direction predictedDirection, Direction actualDirection,
                                    BigDecimal changePct, boolean hit, Instant evaluatedAt) {
        this.id = id;
        this.auditId = auditId;
        this.windowDays = windowDays;
        this.signalGeneratedAt = signalGeneratedAt;
        this.evaluationTargetAt = evaluationTargetAt;
        this.priceAtSignal = priceAtSignal;
        this.priceAtWindowEnd = priceAtWindowEnd;
        this.endTradeDate = endTradeDate;
        this.signal = signal;
        this.predictedDirection = predictedDirection;
        this.actualDirection = actualDirection;
        this.changePct = changePct;
        this.hit = hit;
        this.evaluatedAt = evaluatedAt;
    }

    public UUID getId() { return id; }
    public UUID getAuditId() { return auditId; }
    public short getWindowDays() { return windowDays; }
    public Signal getSignal() { return signal; }
    public boolean isHit() { return hit; }
    public BigDecimal getChangePct() { return changePct; }
    public Direction getPredictedDirection() { return predictedDirection; }
    public Direction getActualDirection() { return actualDirection; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
}
