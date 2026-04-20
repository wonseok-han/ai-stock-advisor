package com.aistockadvisor.stock.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.infra.client.FinnhubClient;
import com.aistockadvisor.stock.infra.client.YahooFinanceClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 시세 조회. Finnhub(15분 지연) → Yahoo Finance(~1분) fallback.
 * Cache: quote:{ticker} (30s, design §3.4).
 */
@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final TypeReference<Quote> TYPE = new TypeReference<>() {
    };

    private final FinnhubClient finnhub;
    private final YahooFinanceClient yahoo;
    private final RedisCacheAdapter cache;

    public QuoteService(FinnhubClient finnhub, YahooFinanceClient yahoo, RedisCacheAdapter cache) {
        this.finnhub = finnhub;
        this.yahoo = yahoo;
        this.cache = cache;
    }

    public Quote getQuote(String ticker) {
        Quote quote = cache.getOrLoad("quote:" + ticker, TYPE, TTL,
                () -> fetch(ticker));
        if (quote == null) {
            throw new BusinessException(ErrorCode.TICKER_NOT_FOUND);
        }
        return quote;
    }

    private Quote fetch(String ticker) {
        // 1차: Yahoo Finance (~1분 지연, API key 불필요)
        try {
            Quote q = yahoo.quote(ticker);
            if (q != null) return q;
        } catch (Exception ex) {
            log.debug("yahoo quote {} failed, fallback to finnhub: {}", ticker, ex.getMessage());
        }

        // 2차: Finnhub (15분 지연)
        log.debug("yahoo quote {} unavailable, using finnhub", ticker);
        return finnhub.quote(ticker);
    }
}
