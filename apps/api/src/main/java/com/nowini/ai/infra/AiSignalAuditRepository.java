package com.nowini.ai.infra;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiSignalAuditRepository extends JpaRepository<AiSignalAuditEntity, UUID> {

    /**
     * window_days 경과했고 아직 평가되지 않은 audit 조회.
     * fallback=true 인 레코드는 제외 (중립 fallback 은 평가 대상 아님).
     *
     * 참조: docs/02-design/features/signal-accuracy.design.md §5, §11.2 Step 3
     */
    @Query("""
            SELECT a FROM AiSignalAuditEntity a
            WHERE a.fallback = FALSE
              AND a.generatedAt <= :maxGeneratedAt
              AND a.generatedAt >= COALESCE(:sinceOrNull, a.generatedAt)
              AND NOT EXISTS (
                    SELECT 1 FROM AiSignalEvaluationEntity e
                    WHERE e.auditId = a.id AND e.windowDays = :windowDays
              )
            ORDER BY a.generatedAt ASC
            """)
    List<AiSignalAuditEntity> findUnevaluated(@Param("maxGeneratedAt") Instant maxGeneratedAt,
                                              @Param("sinceOrNull") Instant sinceOrNull,
                                              @Param("windowDays") short windowDays,
                                              Pageable pageable);

    /**
     * 백필 대상 총 건수 (admin 엔드포인트 응답용).
     *
     * <p>{@code COALESCE(:sinceOrNull, a.generatedAt)} 은 since 가 null 이면 자기 자신과
     * 비교되어 항상 참이 되고, Postgres 가 컬럼 타입에서 파라미터 타입을 추론하게 해
     * "could not determine data type of parameter" 오류를 회피한다.
     */
    @Query("""
            SELECT COUNT(a) FROM AiSignalAuditEntity a
            WHERE a.fallback = FALSE
              AND a.generatedAt <= :maxGeneratedAt
              AND a.generatedAt >= COALESCE(:sinceOrNull, a.generatedAt)
              AND NOT EXISTS (
                    SELECT 1 FROM AiSignalEvaluationEntity e
                    WHERE e.auditId = a.id AND e.windowDays = :windowDays
              )
            """)
    long countUnevaluated(@Param("maxGeneratedAt") Instant maxGeneratedAt,
                          @Param("sinceOrNull") Instant sinceOrNull,
                          @Param("windowDays") short windowDays);
}
