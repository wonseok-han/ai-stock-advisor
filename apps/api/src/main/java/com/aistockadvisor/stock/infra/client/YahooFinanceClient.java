package com.aistockadvisor.stock.infra.client;

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
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)))
                .responseTimeout(TIMEOUT);
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; AIStockAdvisor/1.0)")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
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
