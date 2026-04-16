package com.aistockadvisor.news.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NewsRawRepository extends JpaRepository<NewsRawEntity, UUID> {

    Optional<NewsRawEntity> findByArticleUrlHash(String articleUrlHash);

    /**
     * INSERT ... ON CONFLICT DO NOTHING.
     * PostgreSQL 에서 unique 충돌 시 트랜잭션을 abort 하지 않고 무시.
     * 반환값: 삽입 성공 1, 충돌 무시 0.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
           INSERT INTO news_raw (id, ticker, article_url_hash, source, source_url, title_en, published_at, created_at)
           VALUES (:id, :ticker, :hash, :source, :url, :title, :publishedAt, :createdAt)
           ON CONFLICT (article_url_hash) DO NOTHING
           """)
    int insertIgnoreConflict(
            @Param("id") UUID id,
            @Param("ticker") String ticker,
            @Param("hash") String hash,
            @Param("source") String source,
            @Param("url") String url,
            @Param("title") String title,
            @Param("publishedAt") Instant publishedAt,
            @Param("createdAt") Instant createdAt);

    /** 24h 이내 번역 완료된 뉴스만 조회. 최신순 최대 N건. */
    @Query("""
           SELECT n FROM NewsRawEntity n
           WHERE n.ticker = :ticker
             AND n.translatedAt IS NOT NULL
             AND n.translatedAt >= :since
           ORDER BY n.publishedAt DESC
           """)
    List<NewsRawEntity> findFreshTranslated(
            @Param("ticker") String ticker,
            @Param("since") Instant since);

    /** 원문 수집만 된 뉴스 (번역 대기). */
    @Query("""
           SELECT n FROM NewsRawEntity n
           WHERE n.ticker = :ticker
             AND n.translatedAt IS NULL
           ORDER BY n.publishedAt DESC
           """)
    List<NewsRawEntity> findUntranslatedByTicker(@Param("ticker") String ticker);
}
