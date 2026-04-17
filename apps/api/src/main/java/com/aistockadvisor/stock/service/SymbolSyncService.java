package com.aistockadvisor.stock.service;

import com.aistockadvisor.stock.infra.StockSymbolRepository;
import com.aistockadvisor.stock.infra.client.FinnhubClient;
import com.aistockadvisor.stock.infra.client.FinnhubClient.StockSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * US 주식 심볼 마스터 동기화.
 * - 앱 기동 시 (DB 비어있으면) 비동기 초기 로드
 * - 매일 06:00 UTC (미장 개장 전) 갱신
 *
 * 100건씩 독립 트랜잭션 커밋 (Supabase statement_timeout 120s 대응).
 */
@Service
public class SymbolSyncService {

    private static final Logger log = LoggerFactory.getLogger(SymbolSyncService.class);
    private static final int BATCH_SIZE = 100;

    private static final String UPSERT_SQL = """
            INSERT INTO stock_symbol (symbol, description, type, currency, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (symbol) DO UPDATE SET
              description = EXCLUDED.description,
              type = EXCLUDED.type,
              currency = EXCLUDED.currency,
              updated_at = EXCLUDED.updated_at
            """;

    private final FinnhubClient finnhub;
    private final StockSymbolRepository repository;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public SymbolSyncService(FinnhubClient finnhub, StockSymbolRepository repository,
                             JdbcTemplate jdbc, TransactionTemplate tx) {
        this.finnhub = finnhub;
        this.repository = repository;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (repository.count() == 0) {
            log.info("stock_symbol table empty — starting async initial sync");
            syncAsync();
        } else {
            log.info("stock_symbol table has {} symbols — skipping startup sync", repository.count());
        }
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "UTC")
    public void scheduledSync() {
        log.info("scheduled symbol sync started");
        sync();
    }

    @Async
    public void syncAsync() {
        sync();
    }

    public void sync() {
        try {
            List<StockSymbol> symbols = finnhub.stockSymbols("US");
            if (symbols.isEmpty()) {
                log.warn("finnhub returned 0 symbols — skipping sync");
                return;
            }

            List<StockSymbol> valid = symbols.stream()
                    .filter(s -> s.symbol() != null && !s.symbol().isBlank())
                    .toList();

            log.info("symbol sync: {} valid symbols, inserting in batches of {}", valid.size(), BATCH_SIZE);
            Timestamp now = Timestamp.from(Instant.now());
            int total = 0;
            int failed = 0;

            for (int i = 0; i < valid.size(); i += BATCH_SIZE) {
                List<StockSymbol> chunk = valid.subList(i, Math.min(i + BATCH_SIZE, valid.size()));
                try {
                    tx.executeWithoutResult(status -> {
                        jdbc.batchUpdate(UPSERT_SQL, chunk, chunk.size(), (ps, s) -> {
                            ps.setString(1, s.symbol());
                            ps.setString(2, s.description() != null ? s.description() : s.symbol());
                            ps.setString(3, s.type());
                            ps.setString(4, s.currency());
                            ps.setTimestamp(5, now);
                        });
                    });
                    total += chunk.size();
                } catch (Exception ex) {
                    failed += chunk.size();
                    log.warn("symbol sync batch failed at offset {}: {}", i, ex.getMessage());
                }
                if (total % 5000 == 0 || i + BATCH_SIZE >= valid.size()) {
                    log.info("symbol sync progress: {}/{} (failed: {})", total, valid.size(), failed);
                }
            }

            log.info("symbol sync complete: {} upserted, {} failed out of {}", total, failed, valid.size());
        } catch (Exception ex) {
            log.warn("symbol sync failed: {} — will retry next schedule", ex.getMessage());
        }
    }
}
