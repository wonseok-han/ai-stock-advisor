package com.nowini.market.service;

import com.nowini.cache.RedisCacheAdapter;
import com.nowini.common.error.BusinessException;
import com.nowini.legal.Disclaimers;
import com.nowini.market.domain.MarketIndex;
import com.nowini.market.domain.MarketOverviewResponse;
import com.nowini.market.infra.FmpClient;
import com.nowini.stock.domain.Quote;
import com.nowini.stock.infra.client.FinnhubClient;
import com.nowini.stock.infra.client.StooqClient;
import com.nowini.stock.infra.client.YahooFinanceClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.nowini.stock.domain.MarketStatus;
import com.nowini.stock.domain.MarketStatusResolver;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 시장 개요: 주요 지수(S&P500, Nasdaq, Dow, Russell 2000) + 매크로(VIX, 국채, 금, 유가) + USD/KRW.
 * 심볼별 1차 소스(해석 순서 FMP → Stooq → Yahoo → Finnhub):
 * 지수·VIX·금·은 = FMP(프록시 불필요), 유가·구리·선물(ES·NQ) = Stooq, DXY·국채·USD/KRW = Yahoo.
 * 캐시: market:overview (장중 30분, FMP 250/day 한도 고려).
 * 참조: docs/02-design/features/market-dashboard.design.md §5.1
 */
@Service
public class MarketOverviewService {

    private static final Logger log = LoggerFactory.getLogger(MarketOverviewService.class);
    /** 워밍 주기(30분)보다 길게 잡아 갱신이 항상 만료보다 먼저 일어나게(콜드 빈틈 방지). */
    private static final Duration TTL_OPEN = Duration.ofMinutes(40);
    private static final TypeReference<MarketOverviewResponse> TYPE = new TypeReference<>() {
    };

    private final FinnhubClient finnhubClient;
    private final YahooFinanceClient yahooFinanceClient;
    private final FmpClient fmpClient;
    private final StooqClient stooqClient;
    private final RedisCacheAdapter cache;

    /** 지수 심볼 매핑: {Finnhub, Yahoo, FMP, Stooq, 표시명} (빈값=미사용, 해석 순서 FMP→Stooq→Yahoo→Finnhub) */
    private static final String[][] INDEX_SYMBOLS = {
            {"^GSPC", "^GSPC", "^GSPC", "",     "S&P 500"},
            {"^IXIC", "^IXIC", "^IXIC", "",     "Nasdaq"},
            {"^DJI",  "^DJI",  "^DJI",  "",     "Dow Jones"},
            {"^RUT",  "^RUT",  "^RUT",  "",     "Russell 2000"},
            {"ES=F",  "ES=F",  "",      "es.f", "S&P 500 선물"},
            {"NQ=F",  "NQ=F",  "",      "nq.f", "Nasdaq 선물"},
    };

    /** 매크로 심볼 매핑: {Finnhub, Yahoo, FMP, Stooq, 표시명} */
    private static final String[][] MACRO_SYMBOLS = {
            {"^VIX",  "^VIX",  "^VIX",  "",     "VIX"},
            {"DX-Y.NYB", "DX-Y.NYB", "", "",    "DXY"},
            {"^TNX",  "^TNX",  "",      "",     "10Y Treasury"},
            {"GC=F",  "GC=F",  "GCUSD", "",     "Gold"},
            {"SI=F",  "SI=F",  "SIUSD", "",     "Silver"},
            {"CL=F",  "CL=F",  "",      "cl.f", "WTI Oil"},
            {"HG=F",  "HG=F",  "",      "hg.f", "Copper"},
    };

    public MarketOverviewService(FinnhubClient finnhubClient,
                                 YahooFinanceClient yahooFinanceClient,
                                 FmpClient fmpClient,
                                 StooqClient stooqClient,
                                 RedisCacheAdapter cache) {
        this.finnhubClient = finnhubClient;
        this.yahooFinanceClient = yahooFinanceClient;
        this.fmpClient = fmpClient;
        this.stooqClient = stooqClient;
        this.cache = cache;
    }

    public MarketOverviewResponse getOverview() {
        return cache.getOrLoad("market:overview", TYPE, ttl(), this::fetchOverview);
    }

    /** 캐시 워밍 — 콜드패스 방지용으로 스케줄러가 만료 전 미리 캐시를 덮어쓴다. */
    public void refresh() {
        cache.set("market:overview", fetchOverview(), ttl());
    }

    private Duration ttl() {
        return MarketStatusResolver.resolve() == MarketStatus.OPEN
                ? TTL_OPEN : MarketStatusResolver.durationUntilNextOpen();
    }

    private MarketOverviewResponse fetchOverview() {
        // 심볼별 소스 해석: FMP(가능 시) → Yahoo → Finnhub
        List<MarketIndex> indices = new ArrayList<>();
        for (String[] sym : INDEX_SYMBOLS) {
            MarketIndex idx = resolve(sym);
            if (idx != null) indices.add(idx);
        }

        List<MarketIndex> macro = new ArrayList<>();
        for (String[] sym : MACRO_SYMBOLS) {
            MarketIndex idx = resolve(sym);
            if (idx != null) macro.add(idx);
        }

        // USD/KRW (FMP 무료 미지원 → Yahoo → Finnhub)
        BigDecimal[] forex = resolveForex();

        if (indices.isEmpty() && forex[0] == null) {
            throw new BusinessException(
                    com.nowini.common.error.ErrorCode.UPSTREAM_UNAVAILABLE,
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

    /** sym = {Finnhub, Yahoo, FMP, Stooq, 표시명}. 빈값 컬럼은 건너뛰고 FMP → Stooq → Yahoo → Finnhub 순. */
    private MarketIndex resolve(String[] sym) {
        String finnhubSymbol = sym[0], yahooSymbol = sym[1], fmpSymbol = sym[2], stooqSymbol = sym[3], displayName = sym[4];

        if (!fmpSymbol.isEmpty()) {
            Quote q = tryQuote(() -> fmpClient.quote(fmpSymbol));
            if (q != null) return toMarketIndex(fmpSymbol, displayName, q);
        }

        if (!stooqSymbol.isEmpty()) {
            Quote q = tryQuote(() -> stooqClient.quote(stooqSymbol));
            if (q != null) return toMarketIndex(stooqSymbol, displayName, q);
        }

        Quote q = tryQuote(() -> yahooFinanceClient.quote(yahooSymbol));
        if (q != null) return toMarketIndex(yahooSymbol, displayName, q);

        q = tryQuote(() -> finnhubClient.quote(finnhubSymbol));
        if (q != null) return toMarketIndex(finnhubSymbol, displayName, q);

        log.warn("{} unavailable from all sources", displayName);
        return null;
    }

    private BigDecimal[] resolveForex() {
        Quote q = tryQuote(() -> yahooFinanceClient.quote("USDKRW=X"));
        if (q == null) q = tryQuote(() -> stooqClient.quote("usdkrw"));
        if (q == null) q = tryQuote(() -> finnhubClient.quote("USDKRW=X"));
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
