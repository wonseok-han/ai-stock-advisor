package com.aistockadvisor.stock.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import com.aistockadvisor.stock.domain.Candle;
import com.aistockadvisor.stock.domain.TimeFrame;
import com.aistockadvisor.stock.infra.client.FinnhubClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 캔들 조회. Cache: candle:{ticker}:{tf} (5m for 1D, 1h for 1W+, design §3.4).
 */
@Service
public class CandleService {

    private static final Duration TTL_INTRADAY = Duration.ofMinutes(5);
    private static final Duration TTL_DAILY_PLUS = Duration.ofHours(1);
    private static final TypeReference<List<Candle>> LIST_TYPE = new TypeReference<>() {
    };

    private final FinnhubClient finnhub;
    private final RedisCacheAdapter cache;

    public CandleService(FinnhubClient finnhub, RedisCacheAdapter cache) {
        this.finnhub = finnhub;
        this.cache = cache;
    }

    public List<Candle> getCandles(String ticker, TimeFrame tf) {
        String key = "candle:" + ticker + ":" + tf.code();
        Duration ttl = tf == TimeFrame.D1 ? TTL_INTRADAY : TTL_DAILY_PLUS;
        List<Candle> candles = cache.getOrLoad(key, LIST_TYPE, ttl, () -> {
            long to = Instant.now().getEpochSecond();
            long from = to - tf.lookback().toSeconds();
            return finnhub.candles(ticker, tf.finnhubResolution(), from, to);
        });
        if (candles == null || candles.isEmpty()) {
            throw new BusinessException(ErrorCode.TICKER_NOT_FOUND);
        }
        return candles;
    }
}
