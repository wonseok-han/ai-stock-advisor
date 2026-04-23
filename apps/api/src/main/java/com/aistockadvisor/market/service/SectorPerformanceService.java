package com.aistockadvisor.market.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.market.domain.SectorPerformance;
import com.aistockadvisor.market.infra.FmpClient;
import com.aistockadvisor.market.infra.FmpClient.FmpSectorPerformance;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.infra.client.YahooFinanceClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SectorPerformanceService {

    private static final Logger log = LoggerFactory.getLogger(SectorPerformanceService.class);
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final TypeReference<List<SectorPerformance>> TYPE = new TypeReference<>() {};

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

    public SectorPerformanceService(FmpClient fmpClient, YahooFinanceClient yahooClient,
                                     RedisCacheAdapter cache) {
        this.fmpClient = fmpClient;
        this.yahooClient = yahooClient;
        this.cache = cache;
    }

    public List<SectorPerformance> getSectors() {
        return cache.getOrLoad("market:sectors", TYPE, TTL, this::fetchWithFallback);
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
                .parallel()
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
}
