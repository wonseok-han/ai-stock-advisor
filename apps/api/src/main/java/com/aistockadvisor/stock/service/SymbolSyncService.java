package com.aistockadvisor.stock.service;

import com.aistockadvisor.stock.infra.StockSymbolRepository;
import com.aistockadvisor.stock.infra.client.FinnhubClient;
import com.aistockadvisor.stock.infra.client.FinnhubClient.StockSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * US 주식 심볼 마스터 동기화.
 * - 앱 기동 시 (DB 비어있으면) 초기 로드
 * - 매일 06:00 UTC (미장 개장 전) 갱신
 */
@Service
@Transactional
public class SymbolSyncService {

    private static final Logger log = LoggerFactory.getLogger(SymbolSyncService.class);

    private final FinnhubClient finnhub;
    private final StockSymbolRepository repository;

    public SymbolSyncService(FinnhubClient finnhub, StockSymbolRepository repository) {
        this.finnhub = finnhub;
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (repository.count() == 0) {
            log.info("stock_symbol table empty — running initial sync");
            sync();
        } else {
            log.info("stock_symbol table has {} symbols — skipping startup sync", repository.count());
        }
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "UTC")
    public void scheduledSync() {
        log.info("scheduled symbol sync started");
        sync();
    }

    public void sync() {
        try {
            List<StockSymbol> symbols = finnhub.stockSymbols("US");
            if (symbols.isEmpty()) {
                log.warn("finnhub returned 0 symbols — skipping sync");
                return;
            }
            Instant now = Instant.now();
            int count = 0;
            for (StockSymbol s : symbols) {
                if (s.symbol() == null || s.symbol().isBlank()) continue;
                repository.upsert(s.symbol(), s.description(), s.type(), s.currency(), now);
                count++;
            }
            log.info("symbol sync complete: {} symbols upserted", count);
        } catch (Exception ex) {
            log.warn("symbol sync failed: {} — will retry next schedule", ex.getMessage());
        }
    }
}
