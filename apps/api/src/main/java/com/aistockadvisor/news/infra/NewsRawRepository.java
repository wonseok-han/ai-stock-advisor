package com.aistockadvisor.news.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NewsRawRepository extends JpaRepository<NewsRawEntity, UUID> {

    Optional<NewsRawEntity> findByArticleUrlHash(String articleUrlHash);

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
