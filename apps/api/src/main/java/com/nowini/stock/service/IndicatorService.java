package com.nowini.stock.service;

import com.nowini.cache.RedisCacheAdapter;
import com.nowini.common.error.BusinessException;
import com.nowini.common.error.ErrorCode;
import com.nowini.stock.domain.Candle;
import com.nowini.stock.domain.IndicatorSnapshot;
import com.nowini.stock.domain.MarketStatus;
import com.nowini.stock.domain.MarketStatusResolver;
import com.nowini.stock.domain.TimeFrame;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandFacade;
import org.ta4j.core.indicators.bollinger.PercentBIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * ta4j 기반 RSI/MACD/Bollinger/MA 계산.
 * Source candles: 1Y daily (≥ 200봉 보장 → MA60/Bollinger 안정).
 * Cache: ind:{ticker} (5m, design §3.4).
 */
@Service
public class IndicatorService {

    private static final Duration TTL_OPEN = Duration.ofMinutes(5);
    private static final TimeFrame SOURCE = TimeFrame.Y1;
    private static final TypeReference<IndicatorSnapshot> TYPE = new TypeReference<>() {
    };

    private final CandleService candleService;
    private final RedisCacheAdapter cache;

    public IndicatorService(CandleService candleService, RedisCacheAdapter cache) {
        this.candleService = candleService;
        this.cache = cache;
    }

    public IndicatorSnapshot compute(String ticker) {
        Duration ttl = MarketStatusResolver.resolve() == MarketStatus.OPEN
                ? TTL_OPEN : MarketStatusResolver.durationUntilNextOpen();
        return cache.getOrLoad("ind:v2:" + ticker, TYPE, ttl, () -> doCompute(ticker));
    }

    private IndicatorSnapshot doCompute(String ticker) {
        List<Candle> candles = candleService.getCandles(ticker, SOURCE);
        if (candles.size() < 60) {
            // MA60 안정성 확보 못함 → 산정 자체를 거부 (참고지표는 신뢰도가 핵심).
            throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE,
                    "지표 계산에 필요한 봉 수가 부족합니다.");
        }

        BarSeries series = toSeries(ticker, candles);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        int last = series.getEndIndex();

        double rsi14 = lastDouble(new RSIIndicator(close, 14), last);

        MACDIndicator macd = new MACDIndicator(close, 12, 26);
        EMAIndicator macdSignal = new EMAIndicator(macd, 9);
        double macdValue = lastDouble(macd, last);
        double signalValue = lastDouble(macdSignal, last);

        BollingerBandFacade bb = new BollingerBandFacade(series, 20, 2.0);
        double bbUpper = lastDouble(bb.upper(), last);
        double bbMiddle = lastDouble(bb.middle(), last);
        double bbLower = lastDouble(bb.lower(), last);
        double percentB = lastDouble(new PercentBIndicator(close, 20, 2.0), last);

        double ma5 = lastDouble(new SMAIndicator(close, 5), last);
        double ma20 = lastDouble(new SMAIndicator(close, 20), last);
        double ma60 = lastDouble(new SMAIndicator(close, 60), last);

        long avgVolume20d = computeAvgVolume(candles, 20);

        return new IndicatorSnapshot(
                ticker,
                rsi14,
                new IndicatorSnapshot.Macd(macdValue, signalValue, macdValue - signalValue),
                new IndicatorSnapshot.Bollinger(bbUpper, bbMiddle, bbLower, percentB),
                new IndicatorSnapshot.MovingAverage(ma5, ma20, ma60),
                avgVolume20d,
                IndicatorTooltips.KO
        );
    }

    private static BarSeries toSeries(String ticker, List<Candle> candles) {
        BarSeries series = new BaseBarSeriesBuilder().withName(ticker).build();
        Duration day = Duration.ofDays(1);
        for (Candle c : candles) {
            series.barBuilder()
                    .timePeriod(day)
                    .endTime(Instant.ofEpochSecond(c.time()))
                    .openPrice(c.open())
                    .highPrice(c.high())
                    .lowPrice(c.low())
                    .closePrice(c.close())
                    .volume(c.volume())
                    .add();
        }
        return series;
    }

    private static long computeAvgVolume(List<Candle> candles, int days) {
        int size = candles.size();
        if (size < days) return 0L;
        long sum = 0;
        for (int i = size - days; i < size; i++) {
            sum += candles.get(i).volume();
        }
        return sum / days;
    }

    private static double lastDouble(Indicator<Num> ind, int idx) {
        Num v = ind.getValue(idx);
        if (v == null || v.isNaN()) {
            return 0.0;
        }
        double d = v.doubleValue();
        return Double.isFinite(d) ? d : 0.0;
    }
}
