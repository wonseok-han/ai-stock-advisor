package com.aistockadvisor.stock.infra.client;

import com.aistockadvisor.stock.domain.MarketStatus;
import com.aistockadvisor.stock.domain.MarketStatusResolver;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.infra.CandleEntity;
import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Yahoo Finance v8 chart API 클라이언트.
 * 무료, API key 불필요. 일봉 OHLCV + adjusted close 벌크 다운로드 전용.
 * <p>
 * 엔드포인트: https://query1.finance.yahoo.com/v8/finance/chart/{symbol}
 * 파라미터: period1, period2 (epoch), interval=1d
 * <p>
 * 참조: docs/02-design/features/phase4.5-improvements.design.md §5.1
 */
@Component
public class YahooFinanceClient {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceClient.class);
    private static final String BASE_URL = "https://query1.finance.yahoo.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;

    public YahooFinanceClient() {
        this(BASE_URL);
    }

    /** 테스트 전용: MockWebServer URL 주입 경로. */
    YahooFinanceClient(String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)))
                .responseTimeout(TIMEOUT);
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; AIStockAdvisor/1.0)")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * 현재가 스냅샷. v8/finance/chart meta 필드에서 추출.
     * regularMarketPrice 가 null 이거나 0 이면 null 반환.
     */
    public Quote quote(String ticker) {
        try {
            JsonNode root = webClient.get()
                    .uri(b -> b.path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", "1m")
                            .queryParam("range", "1d")
                            .build(ticker))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(TIMEOUT);

            if (root == null) return null;
            JsonNode result = root.path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) return null;

            JsonNode meta = result.get(0).path("meta");
            BigDecimal price = tobd(meta.path("regularMarketPrice"));
            if (price == null || price.signum() == 0) return null;

            BigDecimal prevClose = tobd(meta.path("chartPreviousClose"));
            if (prevClose == null) prevClose = tobd(meta.path("previousClose"));
            BigDecimal change = prevClose != null && prevClose.signum() > 0
                    ? price.subtract(prevClose) : BigDecimal.ZERO;
            BigDecimal changePct = prevClose != null && prevClose.signum() > 0
                    ? change.divide(prevClose, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            long ts = meta.path("regularMarketTime").asLong(0);
            OffsetDateTime updatedAt = ts > 0
                    ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneOffset.UTC)
                    : OffsetDateTime.now(ZoneOffset.UTC);

            long regularStart = meta.path("currentTradingPeriod").path("regular").path("start").asLong(0);
            long regularEnd   = meta.path("currentTradingPeriod").path("regular").path("end").asLong(0);
            MarketStatus status = MarketStatusResolver.resolveByPeriod(regularStart, regularEnd);

            return new Quote(
                    ticker,
                    price,
                    change,
                    changePct,
                    tobd(meta.path("regularMarketDayHigh")),
                    tobd(meta.path("regularMarketDayLow")),
                    tobd(meta.path("regularMarketOpen")),
                    prevClose,
                    meta.path("regularMarketVolume").asLong(0),
                    updatedAt,
                    status,
                    MarketStatusResolver.priceLabel(status, updatedAt)
            );
        } catch (Exception ex) {
            log.warn("yahoo finance quote {} failed: {}", ticker, ex.getMessage());
            return null;
        }
    }

    private static BigDecimal tobd(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        double v = node.asDouble(0);
        return v == 0 ? null : BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 일봉 OHLCV + adjusted close 다운로드.
     *
     * @param ticker 종목 심볼 (예: AAPL)
     * @param from   시작일 (포함)
     * @param to     종료일 (포함)
     * @return CandleEntity 리스트 (오름차순), 실패 시 빈 리스트
     */
    public List<CandleEntity> fetchDailyCandles(String ticker, LocalDate from, LocalDate to) {
        long period1 = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long period2 = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        try {
            JsonNode root = webClient.get()
                    .uri(b -> b.path("/v8/finance/chart/{symbol}")
                            .queryParam("period1", period1)
                            .queryParam("period2", period2)
                            .queryParam("interval", "1d")
                            .queryParam("events", "div,splits")
                            .build(ticker))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(TIMEOUT);

            return parseChartResponse(ticker, root);
        } catch (Exception ex) {
            log.warn("yahoo finance {} fetch failed: {}", ticker, ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<CandleEntity> parseChartResponse(String ticker, JsonNode root) {
        if (root == null) return Collections.emptyList();

        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            log.warn("yahoo finance {} empty result", ticker);
            return Collections.emptyList();
        }

        JsonNode first = result.get(0);
        JsonNode timestamps = first.path("timestamp");
        JsonNode quote = first.path("indicators").path("quote").get(0);
        JsonNode adjCloseNode = first.path("indicators").path("adjclose");

        if (!timestamps.isArray() || !quote.isObject()) {
            return Collections.emptyList();
        }

        JsonNode opens = quote.path("open");
        JsonNode highs = quote.path("high");
        JsonNode lows = quote.path("low");
        JsonNode closes = quote.path("close");
        JsonNode volumes = quote.path("volume");

        // adjclose 는 별도 배열
        JsonNode adjCloses = adjCloseNode.isArray() && !adjCloseNode.isEmpty()
                ? adjCloseNode.get(0).path("adjclose")
                : null;

        List<CandleEntity> candles = new ArrayList<>(timestamps.size());
        for (int i = 0; i < timestamps.size(); i++) {
            if (timestamps.get(i).isNull() || opens.get(i).isNull() || closes.get(i).isNull()) {
                continue;
            }

            long epoch = timestamps.get(i).asLong();
            LocalDate date = Instant.ofEpochSecond(epoch)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();

            BigDecimal open = toBigDecimal(opens.get(i));
            BigDecimal high = toBigDecimal(highs.get(i));
            BigDecimal low = toBigDecimal(lows.get(i));
            BigDecimal close = toBigDecimal(closes.get(i));
            BigDecimal adjClose = (adjCloses != null && i < adjCloses.size() && !adjCloses.get(i).isNull())
                    ? toBigDecimal(adjCloses.get(i))
                    : close;
            long volume = (volumes.get(i) != null && !volumes.get(i).isNull())
                    ? volumes.get(i).asLong()
                    : 0L;

            candles.add(new CandleEntity(ticker, date, open, high, low, close, adjClose, volume));
        }

        return candles;
    }

    private static BigDecimal toBigDecimal(JsonNode node) {
        if (node == null || node.isNull()) return BigDecimal.ZERO;
        return BigDecimal.valueOf(node.asDouble()).setScale(4, RoundingMode.HALF_UP);
    }
}
