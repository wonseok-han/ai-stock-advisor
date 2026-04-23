package com.aistockadvisor.stock.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.stock.domain.AnalystEstimates;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.infra.client.YahooFinanceClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Service
public class AnalystEstimatesService {

    private static final Logger log = LoggerFactory.getLogger(AnalystEstimatesService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final TypeReference<AnalystEstimates> TYPE = new TypeReference<>() {};

    private final YahooFinanceClient yahooClient;
    private final QuoteService quoteService;
    private final RedisCacheAdapter cache;

    public AnalystEstimatesService(YahooFinanceClient yahooClient,
                                   QuoteService quoteService,
                                   RedisCacheAdapter cache) {
        this.yahooClient = yahooClient;
        this.quoteService = quoteService;
        this.cache = cache;
    }

    public AnalystEstimates getEstimates(String ticker) {
        AnalystEstimates raw = cache.getOrLoad("analyst:" + ticker, TYPE, CACHE_TTL,
                () -> yahooClient.analystEstimates(ticker));
        return enrichCurrentPrice(raw, ticker);
    }

    private AnalystEstimates enrichCurrentPrice(AnalystEstimates data, String ticker) {
        if (data == null || data.priceTarget() == null) return data;
        if (data.priceTarget().current() != null) return data;

        Quote quote = quoteService.getQuote(ticker);
        if (quote == null || quote.price() == null) return data;

        BigDecimal current = quote.price();
        var pt = data.priceTarget();
        BigDecimal upside = pt.mean() != null && current.signum() > 0
                ? pt.mean().subtract(current).multiply(BigDecimal.valueOf(100))
                    .divide(current, 2, RoundingMode.HALF_UP)
                : null;

        return new AnalystEstimates(
                data.rating(),
                new AnalystEstimates.PriceTarget(current, pt.high(), pt.low(), pt.mean(), pt.median(), upside),
                data.earnings()
        );
    }
}
