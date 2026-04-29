package com.aistockadvisor.market.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.legal.Disclaimers;
import com.aistockadvisor.market.domain.MarketIndex;
import com.aistockadvisor.market.domain.MarketOverviewResponse;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.infra.client.FinnhubClient;
import com.aistockadvisor.stock.infra.client.TwelveDataClient;
import com.aistockadvisor.stock.infra.client.YahooFinanceClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.aistockadvisor.stock.domain.MarketStatus;
import com.aistockadvisor.stock.domain.MarketStatusResolver;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 시장 개요: 주요 지수(S&P500, Nasdaq, Dow, Russell 2000) + 매크로(VIX, 국채, 금, 유가) + USD/KRW.
 * 캐시: market:overview (5분).
 * 참조: docs/02-design/features/market-dashboard.design.md §5.1
 */
@Service
public class MarketOverviewService {

    private static final Logger log = LoggerFactory.getLogger(MarketOverviewService.class);
    private static final Duration TTL_OPEN = Duration.ofMinutes(5);
    private static final TypeReference<MarketOverviewResponse> TYPE = new TypeReference<>() {
    };

    private final FinnhubClient finnhubClient;
    private final YahooFinanceClient yahooFinanceClient;
    private final TwelveDataClient twelveDataClient;
    private final RedisCacheAdapter cache;

    /** 지수 심볼 매핑: {Finnhub, Yahoo, TwelveData, 표시명} */
    private static final String[][] INDEX_SYMBOLS = {
            {"^GSPC", "^GSPC", "SPX",  "S&P 500"},
            {"^IXIC", "^IXIC", "IXIC", "Nasdaq"},
            {"^DJI",  "^DJI",  "DJI",  "Dow Jones"},
            {"^RUT",  "^RUT",  "RUT",  "Russell 2000"},
            {"ES=F",  "ES=F",  "ES",   "S&P 500 선물"},
            {"NQ=F",  "NQ=F",  "NQ",   "Nasdaq 선물"},
    };

    /** 매크로 심볼 매핑: {Finnhub, Yahoo, TwelveData, 표시명} */
    private static final String[][] MACRO_SYMBOLS = {
            {"^VIX",  "^VIX",  "VIX",     "VIX"},
            {"DX-Y.NYB", "DX-Y.NYB", "DXY", "DXY"},
            {"^TNX",  "^TNX",  "TNX",     "10Y Treasury"},
            {"GC=F",  "GC=F",  "XAU/USD", "Gold"},
            {"SI=F",  "SI=F",  "XAG/USD", "Silver"},
            {"CL=F",  "CL=F",  "WTI/USD", "WTI Oil"},
            {"HG=F",  "HG=F",  "XCU/USD", "Copper"},
    };

    public MarketOverviewService(FinnhubClient finnhubClient,
                                 YahooFinanceClient yahooFinanceClient,
                                 TwelveDataClient twelveDataClient,
                                 RedisCacheAdapter cache) {
        this.finnhubClient = finnhubClient;
        this.yahooFinanceClient = yahooFinanceClient;
        this.twelveDataClient = twelveDataClient;
        this.cache = cache;
    }

    public MarketOverviewResponse getOverview() {
        Duration ttl = MarketStatusResolver.resolve() == MarketStatus.OPEN
                ? TTL_OPEN : MarketStatusResolver.durationUntilNextOpen();
        return cache.getOrLoad("market:overview", TYPE, ttl, this::fetchOverview);
    }

    /** TwelveData batch에 사용할 전체 심볼 목록 (지수 4 + 매크로 7 + USD/KRW = 12종). */
    private static final List<String> ALL_TWELVE_SYMBOLS;
    static {
        List<String> syms = new ArrayList<>();
        for (String[] s : INDEX_SYMBOLS) syms.add(s[2]);
        for (String[] s : MACRO_SYMBOLS) syms.add(s[2]);
        syms.add("USD/KRW");
        ALL_TWELVE_SYMBOLS = List.copyOf(syms);
    }

    private MarketOverviewResponse fetchOverview() {
        // 1. TwelveData batch quote — 12종을 1 req로 조회
        Map<String, Quote> batch = twelveDataClient.batchQuote(ALL_TWELVE_SYMBOLS);
        log.debug("twelvedata batch: {}/{} symbols returned", batch.size(), ALL_TWELVE_SYMBOLS.size());

        // 2. 지수 조합 (batch hit → Yahoo → Finnhub fallback)
        List<MarketIndex> indices = new ArrayList<>();
        for (String[] sym : INDEX_SYMBOLS) {
            MarketIndex idx = resolveFromBatch(batch, sym[2], sym[3]);
            if (idx == null) idx = fallbackIndex(sym[0], sym[1], sym[3]);
            if (idx != null) indices.add(idx);
        }

        // 3. 매크로 조합 (batch hit → Finnhub → Yahoo fallback)
        List<MarketIndex> macro = new ArrayList<>();
        for (String[] sym : MACRO_SYMBOLS) {
            MarketIndex idx = resolveFromBatch(batch, sym[2], sym[3]);
            if (idx == null) idx = fallbackMacro(sym[0], sym[1], sym[3]);
            if (idx != null) macro.add(idx);
        }

        // 4. USD/KRW (batch hit → Finnhub → Yahoo fallback)
        BigDecimal[] forex = resolveForexFromBatch(batch);

        if (indices.isEmpty() && forex[0] == null) {
            throw new BusinessException(
                    com.aistockadvisor.common.error.ErrorCode.UPSTREAM_UNAVAILABLE,
                    "모든 시장 데이터 소스 실패");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        MarketStatus status = MarketStatusResolver.resolve();
        return new MarketOverviewResponse(
                indices, forex[0], forex[1], macro, now,
                MarketStatusResolver.priceLabel(status, now),
                Disclaimers.MARKET
        );
    }

    private MarketIndex resolveFromBatch(Map<String, Quote> batch, String twelveSymbol, String displayName) {
        Quote q = batch.get(twelveSymbol);
        if (q != null && q.price() != null && q.price().signum() > 0) {
            return toMarketIndex(twelveSymbol, displayName, q);
        }
        return null;
    }

    private BigDecimal[] resolveForexFromBatch(Map<String, Quote> batch) {
        Quote q = batch.get("USD/KRW");
        if (q != null && q.price() != null && q.price().signum() > 0) {
            return new BigDecimal[]{q.price(), resolveChange(q)};
        }
        return fallbackForex();
    }

    private MarketIndex fallbackIndex(String finnhubSymbol, String yahooSymbol, String displayName) {
        Quote q = tryQuote(() -> yahooFinanceClient.quote(yahooSymbol));
        if (q != null) return toMarketIndex(yahooSymbol, displayName, q);

        q = tryQuote(() -> finnhubClient.quote(finnhubSymbol));
        if (q != null) return toMarketIndex(finnhubSymbol, displayName, q);

        log.warn("index {} unavailable from all sources", displayName);
        return null;
    }

    private MarketIndex fallbackMacro(String finnhubSymbol, String yahooSymbol, String displayName) {
        Quote q = tryQuote(() -> finnhubClient.quote(finnhubSymbol));
        if (q != null) return toMarketIndex(finnhubSymbol, displayName, q);

        q = tryQuote(() -> yahooFinanceClient.quote(yahooSymbol));
        if (q != null) return toMarketIndex(yahooSymbol, displayName, q);

        log.warn("macro {} unavailable from all sources", displayName);
        return null;
    }

    private BigDecimal[] fallbackForex() {
        Quote q = tryQuote(() -> finnhubClient.quote("USDKRW=X"));
        if (q != null) return new BigDecimal[]{q.price(), resolveChange(q)};

        q = tryQuote(() -> yahooFinanceClient.quote("USDKRW=X"));
        if (q != null) return new BigDecimal[]{q.price(), resolveChange(q)};

        return new BigDecimal[]{null, null};
    }

    private MarketIndex toMarketIndex(String symbol, String displayName, Quote q) {
        return new MarketIndex(
                symbol, displayName,
                q.price(), q.change(), q.changePercent(), q.updatedAt()
        );
    }

    private Quote tryQuote(Supplier<Quote> supplier) {
        try {
            Quote q = supplier.get();
            if (q != null && q.price() != null && q.price().signum() > 0) {
                return q;
            }
        } catch (BusinessException ex) {
            log.debug("quote fallback: {}", ex.getMessage());
        }
        return null;
    }

    private BigDecimal resolveChange(Quote q) {
        BigDecimal change = q.change();
        if (change != null && change.signum() != 0) {
            return change;
        }
        if (q.previousClose() != null && q.previousClose().signum() > 0) {
            return q.price().subtract(q.previousClose());
        }
        return change;
    }
}
