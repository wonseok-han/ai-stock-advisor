package com.aistockadvisor.stock.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockSymbolRepository extends JpaRepository<StockSymbolEntity, String> {

    /**
     * 심볼 prefix 매치 OR 종목명 유사 검색 (pg_trgm).
     * 심볼 정확 매치 우선 → prefix → 종목명 유사도 순.
     */
    @Query(nativeQuery = true, value = """
           SELECT * FROM stock_symbol
           WHERE upper(symbol) LIKE upper(:q) || '%'
              OR description ILIKE '%' || :q || '%'
           ORDER BY
             CASE WHEN upper(symbol) = upper(:q) THEN 0
                  WHEN upper(symbol) LIKE upper(:q) || '%' THEN 1
                  ELSE 2 END,
             symbol
           LIMIT :lim
           """)
    List<StockSymbolEntity> search(@Param("q") String query, @Param("lim") int limit);

    @Modifying
    @Query(nativeQuery = true, value = """
           INSERT INTO stock_symbol (symbol, description, type, currency, updated_at)
           VALUES (:sym, :desc, :type, :cur, :now)
           ON CONFLICT (symbol) DO UPDATE SET
             description = EXCLUDED.description,
             type = EXCLUDED.type,
             currency = EXCLUDED.currency,
             updated_at = EXCLUDED.updated_at
           """)
    void upsert(@Param("sym") String symbol,
                @Param("desc") String description,
                @Param("type") String type,
                @Param("cur") String currency,
                @Param("now") Instant updatedAt);
}
