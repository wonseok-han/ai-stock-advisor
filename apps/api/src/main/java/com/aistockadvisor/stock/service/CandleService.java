package com.aistockadvisor.stock.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import com.aistockadvisor.stock.domain.Candle;
import com.aistockadvisor.stock.domain.TimeFrame;
import com.aistockadvisor.stock.infra.CandleEntity;
import com.aistockadvisor.stock.infra.CandleRepository;
import com.aistockadvisor.stock.infra.client.TwelveDataClient;
import com.aistockadvisor.stock.infra.client.YahooFinanceClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * 캔들 조회. Phase 4.5: DB-first (daily+) + TwelveData fallback (intraday).
 * <p>
 * D1(intraday): TwelveData 5분봉 + Redis 캐시 (기존 로직).
 * W1~Y5(daily+): DB candles 테이블 → 없으면 Yahoo Finance on-demand 로드 → 비동기 DB persist.
 * 5Y: DB 일봉 → 서비스 레벨 주봉 집계.
 * <p>
 * 참조: docs/02-design/features/phase4.5-improvements.design.md §5.2
 */
@Service
public class CandleService {

    private static final Logger log = LoggerFactory.getLogger(CandleService.class);
    private static final Duration TTL_INTRADAY = Duration.ofMinutes(5);
    private static final TypeReference<List<Candle>> LIST_TYPE = new TypeReference<>() {
    };

    private final TwelveDataClient twelveData;
    private final YahooFinanceClient yahooFinance;
    private final CandleRepository candleRepo;
    private final RedisCacheAdapter cache;

    public CandleService(TwelveDataClient twelveData,
                         YahooFinanceClient yahooFinance,
                         CandleRepository candleRepo,
                         RedisCacheAdapter cache) {
        this.twelveData = twelveData;
        this.yahooFinance = yahooFinance;
        this.candleRepo = candleRepo;
        this.cache = cache;
    }

    public List<Candle> getCandles(String ticker, TimeFrame tf) {
        if (!tf.dbBacked()) {
            return getIntradayCandles(ticker, tf);
        }
        return getDailyCandles(ticker, tf);
    }

    /** D1: 기존 로직 — TwelveData 5분봉 + Redis 캐시. */
    private List<Candle> getIntradayCandles(String ticker, TimeFrame tf) {
        String key = "candle:" + ticker + ":" + tf.code();
        List<Candle> candles = cache.getOrLoad(key, LIST_TYPE, TTL_INTRADAY,
                () -> twelveData.timeSeries(ticker, tf.twelveDataInterval(), tf.outputSize()));
        if (candles == null || candles.isEmpty()) {
            throw new BusinessException(ErrorCode.TICKER_NOT_FOUND);
        }
        return candles;
    }

    /** W1~Y5: DB 우선 → on-demand Yahoo fallback → 비동기 persist. */
    private List<Candle> getDailyCandles(String ticker, TimeFrame tf) {
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = to.minusDays(tf.lookbackDays());

        List<CandleEntity> entities = candleRepo
                .findByTickerAndTradeDateBetweenOrderByTradeDateAsc(ticker, from, to);

        // DB 데이터가 없거나 기대 봉 수의 절반 미만이면 on-demand 로드
        if (entities.size() < tf.outputSize() / 2) {
            List<CandleEntity> fetched = loadAndPersist(ticker, from, to);
            if (fetched.size() > entities.size()) {
                entities = fetched;
            }
        }

        if (entities.isEmpty()) {
            throw new BusinessException(ErrorCode.TICKER_NOT_FOUND);
        }

        if (tf == TimeFrame.Y5) {
            return aggregateWeekly(entities);
        }

        return entities.stream()
                .map(CandleEntity::toCandle)
                .toList();
    }

    /**
     * on-demand: Yahoo Finance에서 일봉 다운로드 → 비동기 DB persist → 즉시 반환.
     */
    private List<CandleEntity> loadAndPersist(String ticker, LocalDate from, LocalDate to) {
        log.info("on-demand candle load: {} [{} ~ {}]", ticker, from, to);
        List<CandleEntity> fetched = yahooFinance.fetchDailyCandles(ticker, from, to);
        if (!fetched.isEmpty()) {
            CompletableFuture.runAsync(() -> {
                try {
                    candleRepo.saveAll(fetched);
                    log.info("on-demand candle persist: {} ({} rows)", ticker, fetched.size());
                } catch (Exception ex) {
                    log.warn("on-demand candle persist failed for {}: {}", ticker, ex.getMessage());
                }
            });
        }
        return fetched;
    }

    /**
     * DB 일봉 → ISO Week 기준 주봉 집계.
     * open=주 첫 봉, close=주 마지막 봉(adjClose), high=max, low=min, volume=sum.
     */
    private List<Candle> aggregateWeekly(List<CandleEntity> dailies) {
        // ISO Year-Week 기준 그룹핑 (TreeMap → 정렬 유지)
        Map<String, List<CandleEntity>> weekGroups = new TreeMap<>();
        for (CandleEntity e : dailies) {
            int isoYear = e.getTradeDate().get(IsoFields.WEEK_BASED_YEAR);
            int isoWeek = e.getTradeDate().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            String key = isoYear + "-W" + String.format("%02d", isoWeek);
            weekGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        List<Candle> weekly = new ArrayList<>(weekGroups.size());
        for (List<CandleEntity> week : weekGroups.values()) {
            CandleEntity first = week.getFirst();
            CandleEntity last = week.getLast();

            BigDecimal high = week.stream().map(CandleEntity::getHigh).reduce(BigDecimal::max).orElse(BigDecimal.ZERO);
            BigDecimal low = week.stream().map(CandleEntity::getLow).reduce(BigDecimal::min).orElse(BigDecimal.ZERO);
            long volume = week.stream().mapToLong(CandleEntity::getVolume).sum();

            // 주봉 시간은 해당 주 월요일 기준
            LocalDate monday = first.getTradeDate().with(DayOfWeek.MONDAY);
            long epochSec = monday.atStartOfDay(ZoneOffset.UTC).toEpochSecond();

            weekly.add(new Candle(epochSec, first.getOpen(), high, low, last.getAdjClose(), volume));
        }

        return weekly;
    }
}
