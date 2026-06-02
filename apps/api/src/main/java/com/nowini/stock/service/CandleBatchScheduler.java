package com.nowini.stock.service;

import com.nowini.stock.infra.CandleEntity;
import com.nowini.stock.infra.CandleRepository;
import com.nowini.stock.infra.client.YahooFinanceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 일간 캔들 배치: DB에 적재된 종목의 당일 일봉을 Yahoo Finance에서 가져와 append.
 * UTC 22:00 (= KST 07:00, EST 17:00) 미장 마감 후 실행.
 * <p>
 * 참조: docs/02-design/features/phase4.5-improvements.design.md §5.3
 */
@Component
@Profile("!local & !test")   // 로컬·테스트에선 스케줄러 끔 (운영과 동시 실행 시 중복 호출 방지)
public class CandleBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(CandleBatchScheduler.class);

    private final CandleRepository candleRepo;
    private final YahooFinanceClient yahooFinance;

    public CandleBatchScheduler(CandleRepository candleRepo, YahooFinanceClient yahooFinance) {
        this.candleRepo = candleRepo;
        this.yahooFinance = yahooFinance;
    }

    @Scheduled(cron = "0 0 22 * * MON-FRI", zone = "UTC")
    public void dailySync() {
        List<String> tickers = candleRepo.findDistinctTickers();
        if (tickers.isEmpty()) {
            log.info("candle batch: no tickers to sync");
            return;
        }

        log.info("candle batch: syncing {} tickers", tickers.size());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int synced = 0;
        int failed = 0;

        for (String ticker : tickers) {
            try {
                LocalDate from = candleRepo.findLatestDate(ticker)
                        .map(d -> d.plusDays(1))
                        .orElse(today);

                if (from.isAfter(today)) {
                    continue;
                }

                List<CandleEntity> candles = yahooFinance.fetchDailyCandles(ticker, from, today);
                if (!candles.isEmpty()) {
                    candleRepo.saveAll(candles);
                    synced += candles.size();
                }
            } catch (Exception ex) {
                log.warn("candle batch sync failed for {}: {}", ticker, ex.getMessage());
                failed++;
            }
        }

        log.info("candle batch: done. synced={} rows, failed={} tickers", synced, failed);
    }
}
