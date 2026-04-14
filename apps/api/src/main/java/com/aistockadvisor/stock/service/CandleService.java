package com.aistockadvisor.stock.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import com.aistockadvisor.stock.domain.Candle;
import com.aistockadvisor.stock.domain.TimeFrame;
import com.aistockadvisor.stock.infra.client.TwelveDataClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 캔들 조회. Cache: candle:{ticker}:{tf} (5m for 1D, 1h for 1W+, design §3.4).
 * Provider: Twelve Data (Finnhub 무료 /candle 403 대응). 기획 04-data-sources hybrid 전략.
 */
@Service
public class CandleService {

    private static final Duration TTL_INTRADAY = Duration.ofMinutes(5);
    private static final Duration TTL_DAILY_PLUS = Duration.ofHours(1);
    private static final TypeReference<List<Candle>> LIST_TYPE = new TypeReference<>() {
    };

    private final TwelveDataClient twelveData;
    private final RedisCacheAdapter cache;

    public CandleService(TwelveDataClient twelveData, RedisCacheAdapter cache) {
        this.twelveData = twelveData;
        this.cache = cache;
    }

    public List<Candle> getCandles(String ticker, TimeFrame tf) {
        String key = "candle:" + ticker + ":" + tf.code();
        Duration ttl = tf == TimeFrame.D1 ? TTL_INTRADAY : TTL_DAILY_PLUS;
        List<Candle> candles = cache.getOrLoad(key, LIST_TYPE, ttl,
                () -> twelveData.timeSeries(ticker, tf.twelveDataInterval(), tf.outputSize()));
        if (candles == null || candles.isEmpty()) {
            throw new BusinessException(ErrorCode.TICKER_NOT_FOUND);
        }
        return candles;
    }
}
