package com.nowini.stock.service;

import com.nowini.cache.RedisCacheAdapter;
import com.nowini.common.error.BusinessException;
import com.nowini.common.error.ErrorCode;
import com.nowini.stock.domain.Candle;
import com.nowini.stock.domain.MarketStatus;
import com.nowini.stock.domain.MarketStatusResolver;
import com.nowini.stock.domain.TimeFrame;
import com.nowini.stock.infra.CandleEntity;
import com.nowini.stock.infra.CandleRepository;
import com.nowini.stock.infra.client.TwelveDataClient;
import com.nowini.stock.infra.client.YahooFinanceClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final Duration TTL_INTRADAY_OPEN = Duration.ofMinutes(5);
    private static final TypeReference<List<Candle>> LIST_TYPE = new TypeReference<>() {
    };

    private final TwelveDataClient twelveData;
    private final YahooFinanceClient yahooFinance;
    private final CandleRepository candleRepo;
    private final RedisCacheAdapter cache;
    private final JdbcTemplate jdbc;

    /**
     * on-demand 캔들 persist 전용 executor(동시 2). 공용 ForkJoinPool 대신 사용해
     * 캔들 적재가 DB 커넥션 풀을 다 잡아 다른 요청을 굶기는 것을 방지한다.
     */
    private final ExecutorService persistExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "candle-persist");
        t.setDaemon(true);
        return t;
    });

    public CandleService(TwelveDataClient twelveData,
                         YahooFinanceClient yahooFinance,
                         CandleRepository candleRepo,
                         RedisCacheAdapter cache,
                         JdbcTemplate jdbc) {
        this.twelveData = twelveData;
        this.yahooFinance = yahooFinance;
        this.candleRepo = candleRepo;
        this.cache = cache;
        this.jdbc = jdbc;
    }

    public List<Candle> getCandles(String ticker, TimeFrame tf) {
        if (!tf.dbBacked()) {
            return getIntradayCandles(ticker, tf);
        }
        return getDailyCandles(ticker, tf);
    }

    /** D1: Yahoo Finance 우선 → TwelveData fallback + Redis 캐시. */
    private List<Candle> getIntradayCandles(String ticker, TimeFrame tf) {
        String key = "candle:" + ticker + ":" + tf.code();
        Duration ttl = MarketStatusResolver.resolve() == MarketStatus.OPEN
                ? TTL_INTRADAY_OPEN : MarketStatusResolver.durationUntilNextOpen();
        List<Candle> candles = cache.getOrLoad(key, LIST_TYPE, ttl,
                () -> fetchIntradayWithFallback(ticker, tf));
        if (candles == null || candles.isEmpty()) {
            throw new BusinessException(ErrorCode.TICKER_NOT_FOUND);
        }
        return candles;
    }

    private List<Candle> fetchIntradayWithFallback(String ticker, TimeFrame tf) {
        List<Candle> candles = yahooFinance.fetchIntradayCandles(ticker);
        if (candles != null && !candles.isEmpty()) {
            return candles;
        }
        log.info("yahoo intraday empty for {}, falling back to twelvedata", ticker);
        return twelveData.timeSeries(ticker, tf.twelveDataInterval(), tf.outputSize());
    }

    /** W1~Y5: DB 우선 → on-demand Yahoo fallback → 비동기 persist. */
    private List<Candle> getDailyCandles(String ticker, TimeFrame tf) {
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = to.minusDays(tf.lookbackDays());

        List<CandleEntity> entities = candleRepo
                .findByTickerAndTradeDateBetweenOrderByTradeDateAsc(ticker, from, to);

        // DB 데이터가 없거나 기대 봉 수의 절반 미만이면 on-demand 로드
        if (entities.size() < tf.outputSize() / 2) {
            // 부분 데이터가 있으면 DB에 없는 과거 구간만 가져옴
            LocalDate fetchFrom = entities.isEmpty()
                    ? from
                    : from.isBefore(entities.get(0).getTradeDate())
                            ? from
                            : entities.get(0).getTradeDate();
            LocalDate fetchTo = entities.isEmpty()
                    ? to
                    : entities.get(0).getTradeDate().minusDays(1);

            if (entities.isEmpty() || !fetchFrom.isAfter(fetchTo)) {
                List<CandleEntity> fetched = loadAndPersist(ticker, fetchFrom,
                        entities.isEmpty() ? to : fetchTo);
                if (!fetched.isEmpty()) {
                    // 새로 가져온 과거 데이터 + 기존 DB 데이터 병합 (시간순)
                    var merged = new java.util.ArrayList<>(fetched);
                    merged.addAll(entities);
                    merged.sort(java.util.Comparator.comparing(CandleEntity::getTradeDate));
                    entities = merged;
                }
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
            persistExecutor.execute(() -> upsertCandles(ticker, fetched));
        }
        return fetched;
    }

    /** 중복키 무시(ON CONFLICT) 배치 upsert. 겹치는 구간 동시 적재 시 candles_pkey 충돌 방지. */
    private void upsertCandles(String ticker, List<CandleEntity> rows) {
        String sql = "INSERT INTO candles (ticker, trade_date, open, high, low, close, adj_close, volume) "
                + "VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (ticker, trade_date) DO NOTHING";
        try {
            jdbc.batchUpdate(sql, rows, rows.size(), (ps, c) -> {
                ps.setString(1, c.getTicker());
                ps.setObject(2, c.getTradeDate());
                ps.setBigDecimal(3, c.getOpen());
                ps.setBigDecimal(4, c.getHigh());
                ps.setBigDecimal(5, c.getLow());
                ps.setBigDecimal(6, c.getClose());
                ps.setBigDecimal(7, c.getAdjClose());
                ps.setLong(8, c.getVolume());
            });
            log.info("on-demand candle persist: {} ({} rows)", ticker, rows.size());
        } catch (Exception ex) {
            log.warn("on-demand candle persist failed for {}: {}", ticker, ex.getMessage());
        }
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
