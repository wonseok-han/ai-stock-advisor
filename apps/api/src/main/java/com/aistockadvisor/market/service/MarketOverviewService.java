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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 시장 개요: 주요 지수(S&P500, Nasdaq, Dow) + VIX + USD/KRW.
 * 캐시: market:overview (5분).
 * 참조: docs/02-design/features/market-dashboard.design.md §5.1
 */
@Service
public class MarketOverviewService {

    private static final Logger log = LoggerFactory.getLogger(MarketOverviewService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final TypeReference<MarketOverviewResponse> TYPE = new TypeReference<>() {
    };

    private final FinnhubClient finnhubClient;
    private final YahooFinanceClient yahooFinanceClient;
    private final TwelveDataClient twelveDataClient;
    private final RedisCacheAdapter cache;

    /** 지수 심볼 매핑: {Finnhub, Yahoo, TwelveData, 표시명} */
    private static final String[][] INDEX_SYMBOLS = {
            {"^GSPC", "^GSPC", "SPX", "S&P 500"},
            {"^IXIC", "^IXIC", "IXIC", "Nasdaq"},
            {"^DJI",  "^DJI",  "DJI", "Dow Jones"},
            {"^VIX",  "^VIX",  "VIX", "VIX"},
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
        return cache.getOrLoad("market:overview", TYPE, CACHE_TTL, this::fetchOverview);
    }

    private MarketOverviewResponse fetchOverview() {
        List<MarketIndex> indices = fetchIndices();
        BigDecimal[] forex = fetchUsdKrw();

        if (indices.isEmpty() && forex[0] == null) {
            throw new BusinessException(
                    com.aistockadvisor.common.error.ErrorCode.UPSTREAM_UNAVAILABLE,
                    "모든 시장 데이터 소스 실패");
        }

        return new MarketOverviewResponse(
                indices,
                forex[0],
                forex[1],
                OffsetDateTime.now(ZoneOffset.UTC),
                Disclaimers.MARKET
        );
    }

    private List<MarketIndex> fetchIndices() {
        return java.util.Arrays.stream(INDEX_SYMBOLS)
                .parallel()
                .map(sym -> fetchIndex(sym[0], sym[1], sym[2], sym[3]))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Finnhub → Yahoo Finance → TwelveData 3단 fallback.
     * 모두 실패 시 null (해당 지수만 응답에서 제외).
     */
    private MarketIndex fetchIndex(String finnhubSymbol, String yahooSymbol,
                                    String twelveSymbol, String displayName) {
        Quote q = tryQuote(() -> finnhubClient.quote(finnhubSymbol));
        if (q != null) return toMarketIndex(finnhubSymbol, displayName, q);

        q = tryQuote(() -> yahooFinanceClient.quote(yahooSymbol));
        if (q != null) return toMarketIndex(yahooSymbol, displayName, q);

        q = tryQuote(() -> twelveDataClient.quote(twelveSymbol));
        if (q != null) return toMarketIndex(twelveSymbol, displayName, q);

        log.warn("index {} unavailable from all sources", displayName);
        return null;
    }

    private MarketIndex toMarketIndex(String symbol, String displayName, Quote q) {
        return new MarketIndex(
                symbol,
                displayName,
                q.price(),
                q.change(),
                q.changePercent(),
                q.updatedAt()
        );
    }

    /**
     * @return BigDecimal[2] — [0]=price, [1]=change (전일 대비 변동). 실패 시 {null, null}.
     */
    private BigDecimal[] fetchUsdKrw() {
        Quote q = tryQuote(() -> finnhubClient.quote("USDKRW=X"));
        if (q != null) return new BigDecimal[]{q.price(), resolveChange(q)};

        q = tryQuote(() -> yahooFinanceClient.quote("USDKRW=X"));
        if (q != null) return new BigDecimal[]{q.price(), resolveChange(q)};

        q = tryQuote(() -> twelveDataClient.quote("USD/KRW"));
        if (q != null) return new BigDecimal[]{q.price(), resolveChange(q)};

        return new BigDecimal[]{null, null};
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
