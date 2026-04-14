package com.aistockadvisor.ai.infra;

import com.aistockadvisor.ai.domain.AiSignal.Signal;
import com.aistockadvisor.ai.domain.AiSignal.Timeframe;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ai_signal_audit 테이블 매핑 (Flyway V4).
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §3.2
 */
@Entity
@Table(name = "ai_signal_audit")
public class AiSignalAuditEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal", nullable = false, length = 16)
    private Signal signal;

    @Column(name = "confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "timeframe", nullable = false, length = 8)
    private Timeframe timeframe;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rationale", nullable = false, columnDefinition = "jsonb")
    private List<String> rationale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risks", nullable = false, columnDefinition = "jsonb")
    private List<String> risks;

    @Column(name = "summary_ko", nullable = false, columnDefinition = "TEXT")
    private String summaryKo;

    @Column(name = "model_name", nullable = false, length = 64)
    private String modelName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> contextPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private Map<String, Object> rawResponse;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forbidden_detected", columnDefinition = "jsonb")
    private List<String> forbiddenDetected;

    @Column(name = "fallback", nullable = false)
    private boolean fallback;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected AiSignalAuditEntity() {
    }

    public AiSignalAuditEntity(UUID id, String ticker, UUID requestId, Signal signal,
                               BigDecimal confidence, Timeframe timeframe,
                               List<String> rationale, List<String> risks,
                               String summaryKo, String modelName,
                               Map<String, Object> contextPayload,
                               Map<String, Object> rawResponse, List<String> forbiddenDetected,
                               boolean fallback, int latencyMs, Integer tokensIn, Integer tokensOut,
                               Instant generatedAt) {
        this.id = id;
        this.ticker = ticker;
        this.requestId = requestId;
        this.signal = signal;
        this.confidence = confidence;
        this.timeframe = timeframe;
        this.rationale = rationale;
        this.risks = risks;
        this.summaryKo = summaryKo;
        this.modelName = modelName;
        this.contextPayload = contextPayload;
        this.rawResponse = rawResponse;
        this.forbiddenDetected = forbiddenDetected;
        this.fallback = fallback;
        this.latencyMs = latencyMs;
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
        this.generatedAt = generatedAt;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public boolean isFallback() { return fallback; }
}
