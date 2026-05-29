package com.nowini.market.service;

import com.nowini.cache.RedisCacheAdapter;
import com.nowini.market.domain.SectorMomentum;
import com.nowini.market.domain.SectorPerformance;
import com.nowini.market.infra.FmpClient;
import com.nowini.market.infra.FmpClient.FmpSectorPerformance;
import com.nowini.stock.domain.Quote;
import com.nowini.stock.infra.CandleEntity;
import com.nowini.stock.infra.client.YahooFinanceClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.nowini.stock.domain.MarketStatus;
import com.nowini.stock.domain.MarketStatusResolver;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SectorPerformanceService {

    private static final Logger log = LoggerFactory.getLogger(SectorPerformanceService.class);
    private static final Duration TTL_OPEN = Duration.ofMinutes(15);
    /** 분기 모멘텀은 일 단위로만 의미가 있어 길게 캐시(장 마감까지). */
    private static final Duration TTL_QUARTERLY = Duration.ofHours(12);
    private static final TypeReference<List<SectorPerformance>> TYPE = new TypeReference<>() {};
    private static final TypeReference<List<SectorMomentum>> MOMENTUM_TYPE = new TypeReference<>() {};

    private final FmpClient fmpClient;
    private final YahooFinanceClient yahooClient;
    private final RedisCacheAdapter cache;

    private static final Map<String, String> SECTOR_KO = Map.ofEntries(
            Map.entry("Technology", "기술"),
            Map.entry("Healthcare", "헬스케어"),
            Map.entry("Financial Services", "금융"),
            Map.entry("Consumer Cyclical", "임의소비재"),
            Map.entry("Communication Services", "커뮤니케이션"),
            Map.entry("Industrials", "산업재"),
            Map.entry("Consumer Defensive", "필수소비재"),
            Map.entry("Energy", "에너지"),
            Map.entry("Utilities", "유틸리티"),
            Map.entry("Real Estate", "부동산"),
            Map.entry("Basic Materials", "소재")
    );

    private static final String[][] SECTOR_ETFS = {
            {"XLK", "Technology", "기술"},
            {"XLV", "Healthcare", "헬스케어"},
            {"XLF", "Financial Services", "금융"},
            {"XLY", "Consumer Cyclical", "임의소비재"},
            {"XLC", "Communication Services", "커뮤니케이션"},
            {"XLI", "Industrials", "산업재"},
            {"XLP", "Consumer Defensive", "필수소비재"},
            {"XLE", "Energy", "에너지"},
            {"XLU", "Utilities", "유틸리티"},
            {"XLRE", "Real Estate", "부동산"},
            {"XLB", "Basic Materials", "소재"},
    };

    /**
     * 테마/산업 ETF — 섹터보다 세분화된 테마별 모멘텀(Finviz 테마 맵 느낌).
     * 유효하지 않은 티커는 fetch 실패 시 자동 제외되므로 넉넉히 정의.
     */
    private static final String[][] THEME_ETFS = {
            {"SOXX", "Semiconductors", "반도체"},
            {"SKYY", "Cloud Computing", "클라우드"},
            {"CIBR", "Cybersecurity", "사이버보안"},
            {"BOTZ", "Robotics & AI", "로봇·AI"},
            {"FDN", "Internet", "인터넷"},
            {"FINX", "FinTech", "핀테크"},
            {"DRIV", "EV & Autonomous", "전기차·자율주행"},
            {"LIT", "Lithium & Battery", "리튬·배터리"},
            {"ICLN", "Clean Energy", "클린에너지"},
            {"TAN", "Solar", "태양광"},
            {"XBI", "Biotech", "바이오텍"},
            {"ITA", "Aerospace & Defense", "항공·방산"},
            {"PAVE", "Infrastructure", "인프라"},
            {"GDX", "Gold Miners", "금광"},
            {"ESPO", "Gaming & eSports", "게임·e스포츠"},
            {"KRE", "Regional Banks", "지역은행"},
            {"XHB", "Homebuilders", "주택건설"},
            {"JETS", "Airlines", "항공사"},
            {"IYT", "Transportation", "운송"},
            {"XME", "Metals & Mining", "금속·광업"},
    };

    public SectorPerformanceService(FmpClient fmpClient, YahooFinanceClient yahooClient,
                                     RedisCacheAdapter cache) {
        this.fmpClient = fmpClient;
        this.yahooClient = yahooClient;
        this.cache = cache;
    }

    public List<SectorPerformance> getSectors() {
        Duration ttl = MarketStatusResolver.resolve() == MarketStatus.OPEN
                ? TTL_OPEN : MarketStatusResolver.durationUntilNextOpen();
        return cache.getOrLoad("market:sectors", TYPE, ttl, this::fetchWithFallback);
    }

    private List<SectorPerformance> fetchWithFallback() {
        List<SectorPerformance> sectors = fetchFromFmp();
        if (!sectors.isEmpty()) return sectors;

        log.debug("FMP sector-performance fallback to Yahoo ETFs");
        return fetchFromYahooEtfs();
    }

    private List<SectorPerformance> fetchFromFmp() {
        List<FmpSectorPerformance> raw = fmpClient.sectorPerformance();
        return raw.stream()
                .filter(s -> s.sector() != null && s.changePercent() != null)
                .map(s -> new SectorPerformance(
                        s.sector(),
                        SECTOR_KO.getOrDefault(s.sector(), s.sector()),
                        s.changePercent()))
                .sorted(Comparator.comparingDouble(SectorPerformance::changePercent).reversed())
                .toList();
    }

    private List<SectorPerformance> fetchFromYahooEtfs() {
        return java.util.Arrays.stream(SECTOR_ETFS)
                .map(etf -> {
                    try {
                        Quote q = yahooClient.quote(etf[0]);
                        if (q != null && q.changePercent() != null) {
                            return new SectorPerformance(etf[1], etf[2],
                                    q.changePercent().doubleValue());
                        }
                    } catch (Exception ex) {
                        log.debug("Yahoo ETF {} failed: {}", etf[0], ex.getMessage());
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(SectorPerformance::changePercent).reversed())
                .toList();
    }

    /**
     * 섹터 분기 모멘텀 — 최근 3개월(롤링) GICS 11섹터 ETF 누적 수익률(%). 강세→약세 정렬.
     */
    public List<SectorMomentum> getQuarterlyMomentum() {
        return cache.getOrLoad("market:sectors:quarterly", MOMENTUM_TYPE, TTL_QUARTERLY,
                () -> fetchMomentum(SECTOR_ETFS));
    }

    /**
     * 테마 분기 모멘텀 — 최근 3개월 테마/산업 ETF 누적 수익률(%). 섹터보다 세분화. 강세→약세 정렬.
     */
    public List<SectorMomentum> getQuarterlyThemes() {
        return cache.getOrLoad("market:themes:quarterly", MOMENTUM_TYPE, TTL_QUARTERLY,
                () -> fetchMomentum(THEME_ETFS));
    }

    /**
     * ETF 묶음의 최근 3개월 누적 수익률 계산. 일봉(adjClose) (최신/3개월전 - 1)×100.
     * 실데이터 기반(환각 없음). 조회 실패·무효 티커는 자동 제외.
     */
    private List<SectorMomentum> fetchMomentum(String[][] etfs) {
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        // 3개월 ≈ 63거래일. 주말·공휴일 버퍼로 달력 100일 조회 후 가장 오래된 캔들을 기준점으로.
        LocalDate from = to.minusDays(100);
        return java.util.Arrays.stream(etfs)
                .map(etf -> {
                    try {
                        List<CandleEntity> candles = yahooClient.fetchDailyCandles(etf[0], from, to);
                        if (candles == null || candles.size() < 2) return null;
                        double start = priceOf(candles.get(0));
                        double end = priceOf(candles.get(candles.size() - 1));
                        if (start <= 0) return null;
                        double ret = (end / start - 1) * 100;
                        return new SectorMomentum(etf[1], etf[2], round(ret));
                    } catch (Exception ex) {
                        log.debug("Yahoo ETF {} quarterly candles failed: {}", etf[0], ex.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(SectorMomentum::returnPct).reversed())
                .toList();
    }

    /** adjClose 우선, 없으면 close. 배당·분할 보정가로 수익률 정확도 확보. */
    private static double priceOf(CandleEntity c) {
        BigDecimal p = c.getAdjClose() != null ? c.getAdjClose() : c.getClose();
        return p != null ? p.doubleValue() : 0;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
