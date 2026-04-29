package com.nowini.ai.infra;

import com.nowini.ai.domain.AiSignal.Signal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * ai_signal_evaluation CRUD + 집계.
 * 참조: docs/02-design/features/signal-accuracy.design.md §11.1
 */
public interface AiSignalEvaluationRepository extends JpaRepository<AiSignalEvaluationEntity, UUID> {

    boolean existsByAuditIdAndWindowDays(UUID auditId, short windowDays);

    /**
     * 전역 집계 (window 별 총 샘플 수 + hit 수).
     */
    @Query("""
            SELECT COUNT(e) AS total,
                   SUM(CASE WHEN e.hit = TRUE THEN 1 ELSE 0 END) AS hits
            FROM AiSignalEvaluationEntity e
            WHERE e.windowDays = :windowDays
            """)
    TotalAndHits aggregateTotal(@Param("windowDays") short windowDays);

    /**
     * Signal 별 집계 (window 별).
     */
    @Query("""
            SELECT e.signal AS signal,
                   COUNT(e) AS total,
                   SUM(CASE WHEN e.hit = TRUE THEN 1 ELSE 0 END) AS hits
            FROM AiSignalEvaluationEntity e
            WHERE e.windowDays = :windowDays
            GROUP BY e.signal
            """)
    List<SignalBucket> aggregateBySignal(@Param("windowDays") short windowDays);

    @Query("""
            SELECT MAX(e.evaluatedAt)
            FROM AiSignalEvaluationEntity e
            WHERE e.windowDays = :windowDays
            """)
    java.time.Instant findLatestEvaluatedAt(@Param("windowDays") short windowDays);

    interface TotalAndHits {
        Long getTotal();
        Long getHits();
    }

    interface SignalBucket {
        Signal getSignal();
        Long getTotal();
        Long getHits();
    }
}
