package com.aistockadvisor.stock.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import com.aistockadvisor.stock.domain.StockProfile;
import com.aistockadvisor.stock.infra.client.FinnhubClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 종목 마스터 조회. Cache: profile:{ticker} (24h, design §3.4).
 */
@Service
public class StockProfileService {

    private static final Duration TTL = Duration.ofHours(24);
    private static final TypeReference<StockProfile> TYPE = new TypeReference<>() {
    };

    private final FinnhubClient finnhub;
    private final RedisCacheAdapter cache;

    public StockProfileService(FinnhubClient finnhub, RedisCacheAdapter cache) {
        this.finnhub = finnhub;
        this.cache = cache;
    }

    public StockProfile getProfile(String ticker) {
        StockProfile profile = cache.getOrLoad("profile:" + ticker, TYPE, TTL,
                () -> finnhub.profile(ticker));
        if (profile == null) {
            throw new BusinessException(ErrorCode.TICKER_NOT_FOUND);
        }
        return profile;
    }
}
