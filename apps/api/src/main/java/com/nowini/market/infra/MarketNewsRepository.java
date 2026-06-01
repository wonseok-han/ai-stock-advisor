package com.nowini.market.infra;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MarketNewsRepository extends JpaRepository<MarketNewsEntity, UUID> {

    /**
     * INSERT ... ON CONFLICT DO NOTHING. 중복 기사(article_url_hash)는 무시.
     * 반환: 삽입 1, 충돌 무시 0.
     */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
           INSERT INTO market_news (id, article_url_hash, source, source_url, title_en, summary_en, published_at, created_at)
           VALUES (:id, :hash, :source, :url, :titleEn, :summaryEn, :publishedAt, :createdAt)
           ON CONFLICT (article_url_hash) DO NOTHING
           """)
    int insertIgnoreConflict(
            @Param("id") UUID id,
            @Param("hash") String hash,
            @Param("source") String source,
            @Param("url") String url,
            @Param("titleEn") String titleEn,
            @Param("summaryEn") String summaryEn,
            @Param("publishedAt") Instant publishedAt,
            @Param("createdAt") Instant createdAt);

    /** 번역 대기(translated_at IS NULL) 항목 — 배치 번역용. 최신순. */
    @Query("""
           SELECT n FROM MarketNewsEntity n
           WHERE n.translatedAt IS NULL
           ORDER BY n.publishedAt DESC
           """)
    List<MarketNewsEntity> findUntranslated(Pageable pageable);

    /** 커서 페이지네이션 — published_at 이 before 보다 과거인 항목을 최신순. */
    @Query("""
           SELECT n FROM MarketNewsEntity n
           WHERE n.publishedAt < :before
           ORDER BY n.publishedAt DESC
           """)
    List<MarketNewsEntity> findPageBefore(@Param("before") Instant before, Pageable pageable);
}
