package com.aistockadvisor.market.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.legal.Disclaimers;
import com.aistockadvisor.market.domain.MarketMover;
import com.aistockadvisor.market.domain.MarketMoversResponse;
import com.aistockadvisor.market.infra.FmpClient;
import com.aistockadvisor.market.infra.FmpClient.FmpMover;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 급등/급락 종목. FMP biggest-gainers / biggest-losers API 사용.
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

    private final FmpClient fmpClient;
    private final RedisCacheAdapter cache;

    public MarketMoversService(FmpClient fmpClient, RedisCacheAdapter cache) {
        this.fmpClient = fmpClient;
        this.cache = cache;
    }

    public MarketMoversResponse getMovers() {
        return cache.getOrLoad("market:movers", TYPE, CACHE_TTL, this::fetchMovers);
    }

    private MarketMoversResponse fetchMovers() {
        List<MarketMover> gainers = fmpClient.gainers().stream()
                .limit(TOP_N)
                .map(this::toMover)
                .toList();

        List<MarketMover> losers = fmpClient.losers().stream()
                .limit(TOP_N)
                .map(this::toMover)
                .toList();

        return new MarketMoversResponse(
                gainers,
                losers,
                gainers.size() + losers.size(),
                OffsetDateTime.now(ZoneOffset.UTC),
                Disclaimers.MARKET_MOVERS
        );
    }

    private MarketMover toMover(FmpMover m) {
        return new MarketMover(
                m.symbol(),
                m.name(),
                m.price(),
                m.change(),
                m.changesPercentage(),
                0L // FMP movers에 volume 미포함
        );
    }
}
