package com.aistockadvisor.stock.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 일봉 캔들 CRUD. PK = (ticker, tradeDate).
 * 참조: docs/02-design/features/phase4.5-improvements.design.md §3.3
 */
public interface CandleRepository extends JpaRepository<CandleEntity, CandleId> {

    List<CandleEntity> findByTickerAndTradeDateBetweenOrderByTradeDateAsc(
            String ticker, LocalDate from, LocalDate to);

    @Query("SELECT DISTINCT c.ticker FROM CandleEntity c")
    List<String> findDistinctTickers();

    @Query("SELECT MAX(c.tradeDate) FROM CandleEntity c WHERE c.ticker = :ticker")
    Optional<LocalDate> findLatestDate(@Param("ticker") String ticker);
}
