package com.aistockadvisor.market.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.legal.Disclaimers;
import com.aistockadvisor.market.domain.MarketIndex;
import com.aistockadvisor.market.domain.MarketOverviewResponse;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.infra.client.FinnhubClient;
import com.aistockadvisor.stock.infra.client.TwelveDataClient;
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
    private final TwelveDataClient twelveDataClient;
    private final RedisCacheAdapter cache;

    /** 지수 심볼 매핑: {Finnhub 심볼, TwelveData 심볼, 표시명} */
    private static final String[][] INDEX_SYMBOLS = {
            {"^GSPC", "SPX", "S&P 500"},
            {"^IXIC", "IXIC", "Nasdaq"},
            {"^DJI", "DJI", "Dow Jones"},
            {"^VIX", "VIX", "VIX"},
    };

    public MarketOverviewService(FinnhubClient finnhubClient,
                                 TwelveDataClient twelveDataClient,
                                 RedisCacheAdapter cache) {
        this.finnhubClient = finnhubClient;
        this.twelveDataClient = twelveDataClient;
        this.cache = cache;
    }

    public MarketOverviewResponse getOverview() {
        return cache.getOrLoad("market:overview", TYPE, CACHE_TTL, this::fetchOverview);
    }

    private MarketOverviewResponse fetchOverview() {
        List<MarketIndex> indices = fetchIndices();
        BigDecimal usdKrw = fetchUsdKrw();

        if (indices.isEmpty() && usdKrw == null) {
            throw new BusinessException(
                    com.aistockadvisor.common.error.ErrorCode.UPSTREAM_UNAVAILABLE,
                    "모든 시장 데이터 소스 실패");
        }

        return new MarketOverviewResponse(
                indices,
                usdKrw,
                null, // 환율 전일 대비 변동 — 별도 캐시 로직 불필요 (Phase 3.1 개선 가능)
                OffsetDateTime.now(ZoneOffset.UTC),
                Disclaimers.MARKET
        );
    }

    private List<MarketIndex> fetchIndices() {
        return java.util.Arrays.stream(INDEX_SYMBOLS)
                .parallel()
                .map(sym -> fetchIndex(sym[0], sym[1], sym[2]))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Finnhub 우선 → TwelveData fallback.
     * 둘 다 실패 시 null (해당 지수만 응답에서 제외).
     */
    private MarketIndex fetchIndex(String finnhubSymbol, String twelveSymbol, String displayName) {
        // 1차: Finnhub
        try {
            Quote q = finnhubClient.quote(finnhubSymbol);
            if (q != null && q.price() != null && q.price().signum() > 0) {
                return toMarketIndex(finnhubSymbol, displayName, q);
            }
        } catch (BusinessException ex) {
            log.debug("finnhub index {} failed: {}", finnhubSymbol, ex.getMessage());
        }

        // 2차: TwelveData fallback
        try {
            Quote q = twelveDataClient.quote(twelveSymbol);
            if (q != null && q.price() != null && q.price().signum() > 0) {
                return toMarketIndex(twelveSymbol, displayName, q);
            }
        } catch (BusinessException ex) {
            log.warn("twelvedata index {} also failed: {}", twelveSymbol, ex.getMessage());
        }

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

    private BigDecimal fetchUsdKrw() {
        // Finnhub /forex/rates 사용
        try {
            Quote q = finnhubClient.quote("USDKRW=X");
            if (q != null && q.price() != null && q.price().signum() > 0) {
                return q.price();
            }
        } catch (BusinessException ex) {
            log.debug("finnhub forex USDKRW failed: {}", ex.getMessage());
        }

        // TwelveData fallback
        try {
            Quote q = twelveDataClient.quote("USD/KRW");
            if (q != null && q.price() != null && q.price().signum() > 0) {
                return q.price();
            }
        } catch (BusinessException ex) {
            log.warn("twelvedata forex USD/KRW also failed: {}", ex.getMessage());
        }

        return null;
    }
}
