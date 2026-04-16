package com.aistockadvisor.market.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.legal.Disclaimers;
import com.aistockadvisor.market.domain.MarketMover;
import com.aistockadvisor.market.domain.MarketMoversResponse;
import com.aistockadvisor.market.infra.PopularTickerPool;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.domain.StockProfile;
import com.aistockadvisor.stock.infra.client.FinnhubClient;
import com.aistockadvisor.stock.service.StockProfileService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 급등/급락 종목. 인기 종목 풀 기반 quote 일괄 → 변동률 정렬.
 * 캐시: market:movers (15분).
 * 참조: docs/02-design/features/market-dashboard.design.md §5.3
 */
@Service
public class MarketMoversService {

    private static final Logger log = LoggerFactory.getLogger(MarketMoversService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final TypeReference<MarketMoversResponse> TYPE = new TypeReference<>() {
    };
    private static final int TOP_N = 5;

    private final FinnhubClient finnhubClient;
    private final StockProfileService profileService;
    private final RedisCacheAdapter cache;

    public MarketMoversService(FinnhubClient finnhubClient,
                               StockProfileService profileService,
                               RedisCacheAdapter cache) {
        this.finnhubClient = finnhubClient;
        this.profileService = profileService;
        this.cache = cache;
    }

    public MarketMoversResponse getMovers() {
        return cache.getOrLoad("market:movers", TYPE, CACHE_TTL, this::fetchMovers);
    }

    private MarketMoversResponse fetchMovers() {
        List<TickerQuote> quotes = PopularTickerPool.TICKERS.parallelStream()
                .map(this::safeQuote)
                .filter(Objects::nonNull)
                .filter(tq -> tq.quote.changePercent() != null)
                .toList();

        List<MarketMover> gainers = quotes.stream()
                .filter(tq -> tq.quote.changePercent().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing((TickerQuote tq) -> tq.quote.changePercent()).reversed())
                .limit(TOP_N)
                .map(this::toMover)
                .toList();

        List<MarketMover> losers = quotes.stream()
                .filter(tq -> tq.quote.changePercent().compareTo(BigDecimal.ZERO) < 0)
                .sorted(Comparator.comparing((TickerQuote tq) -> tq.quote.changePercent()))
                .limit(TOP_N)
                .map(this::toMover)
                .toList();

        return new MarketMoversResponse(
                gainers,
                losers,
                PopularTickerPool.TICKERS.size(),
                OffsetDateTime.now(ZoneOffset.UTC),
                Disclaimers.MARKET_MOVERS
        );
    }

    private TickerQuote safeQuote(String ticker) {
        try {
            Quote q = finnhubClient.quote(ticker);
            return q != null ? new TickerQuote(ticker, q) : null;
        } catch (Exception ex) {
            log.debug("movers quote failed ticker={}: {}", ticker, ex.getMessage());
            return null;
        }
    }

    private MarketMover toMover(TickerQuote tq) {
        String name = resolveTickerName(tq.ticker);
        return new MarketMover(
                tq.ticker,
                name,
                tq.quote.price(),
                tq.quote.change(),
                tq.quote.changePercent(),
                tq.quote.volume()
        );
    }

    private String resolveTickerName(String ticker) {
        try {
            StockProfile profile = profileService.getProfile(ticker);
            return profile.name();
        } catch (Exception ex) {
            return ticker;
        }
    }

    private record TickerQuote(String ticker, Quote quote) {
    }
}
