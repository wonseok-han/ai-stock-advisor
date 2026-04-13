package com.aistockadvisor.stock.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.infra.client.FinnhubClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 시세 조회. Cache: quote:{ticker} (30s, design §3.4).
 */
@Service
public class QuoteService {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final TypeReference<Quote> TYPE = new TypeReference<>() {
    };

    private final FinnhubClient finnhub;
    private final RedisCacheAdapter cache;

    public QuoteService(FinnhubClient finnhub, RedisCacheAdapter cache) {
        this.finnhub = finnhub;
        this.cache = cache;
    }

    public Quote getQuote(String ticker) {
        Quote quote = cache.getOrLoad("quote:" + ticker, TYPE, TTL,
                () -> finnhub.quote(ticker));
        if (quote == null) {
            throw new BusinessException(ErrorCode.TICKER_NOT_FOUND);
        }
        return quote;
    }
}
