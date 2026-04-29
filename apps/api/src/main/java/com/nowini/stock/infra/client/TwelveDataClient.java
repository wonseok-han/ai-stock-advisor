package com.nowini.stock.infra.client;

import com.nowini.cache.RedisCacheAdapter;
import com.nowini.common.error.BusinessException;
import com.nowini.common.error.ErrorCode;
import com.nowini.stock.domain.Candle;
import com.nowini.stock.domain.MarketStatus;
import com.nowini.stock.domain.MarketStatusResolver;
import com.nowini.stock.domain.Quote;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Twelve Data REST 어댑터. OHLCV 캔들 전용.
 * Finnhub 무료 /stock/candle 이 403 이므로 이 client 가 candle 를 담당.
 * <p>무료 플랜: 8 req/min, 800 req/day (Service 캐시로 통제).
 * <p>응답 정렬은 Twelve Data 가 기본 desc(최신→오래) → asc 로 뒤집어 반환.
 * <p>datetime 포맷: 일봉 이상 "yyyy-MM-dd", 분봉 "yyyy-MM-dd HH:mm:ss" (거래소 로컬 시간).
 */
@Component
public class TwelveDataClient {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final Duration TTL_QUOTE_OPEN = Duration.ofSeconds(30);
    private static final Duration TTL_SERIES_OPEN = Duration.ofMinutes(5);
    private static final DateTimeFormatter DATE_ONLY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration BATCH_TIMEOUT = Duration.ofSeconds(8);
    private static final TypeReference<TwelveQuoteResponse> QUOTE_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, TwelveQuoteResponse>> BATCH_QUOTE_TYPE = new TypeReference<>() {};
    private static final TypeReference<TimeSeriesResponse> SERIES_TYPE = new TypeReference<>() {};

    private final WebClient webClient;
    private final String apiKey;
    private final RedisCacheAdapter cache;

    public TwelveDataClient(TwelveDataProperties props, RedisCacheAdapter cache) {
        this.apiKey = props.apiKey();
        this.cache = cache;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)))
                .responseTimeout(TIMEOUT);
        this.webClient = WebClient.builder()
                .baseUrl(props.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * /quote. 지수/종목 실시간 시세 조회.
     * 지수: symbol = "SPX", "IXIC", "DJI", "VIX" 등.
     * 반환: Quote record (기존 도메인 재사용). 데이터 없으면 null.
     */
    public Quote quote(String symbol) {
        Duration ttl = MarketStatusResolver.resolve() == MarketStatus.OPEN
                ? TTL_QUOTE_OPEN : MarketStatusResolver.durationUntilNextOpen();
        TwelveQuoteResponse resp = cache.getOrLoad("twelve:quote:" + symbol, QUOTE_TYPE,
                ttl, () -> call("/quote", uri -> uri
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey), TwelveQuoteResponse.class));
        return toQuote(symbol, resp);
    }

    /**
     * /quote (batch). 여러 심볼을 콤마로 묶어 1회 호출. 12종 = 1 req.
     * 응답: 2종 이상이면 Map{symbol → quote}, 1종이면 단일 객체.
     * 실패 심볼은 결과에서 제외.
     */
    public Map<String, Quote> batchQuote(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Map.of();
        String joined = String.join(",", symbols);
        Duration ttl = MarketStatusResolver.resolve() == MarketStatus.OPEN
                ? TTL_QUOTE_OPEN : MarketStatusResolver.durationUntilNextOpen();
        String cacheKey = "twelve:batch:" + joined.hashCode();

        Map<String, Quote> cached = cache.get(cacheKey,
                new TypeReference<Map<String, Quote>>() {});
        if (cached != null) return cached;

        Map<String, Quote> result = new HashMap<>();
        try {
            if (symbols.size() == 1) {
                Quote q = quote(symbols.getFirst());
                if (q != null) result.put(symbols.getFirst(), q);
            } else {
                String json = webClient.get()
                        .uri(b -> b.path("/quote")
                                .queryParam("symbol", joined)
                                .queryParam("apikey", apiKey)
                                .build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(BATCH_TIMEOUT);
                if (json != null && !json.contains("\"code\"")) {
                    var om = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<String, TwelveQuoteResponse> batch = om.readValue(json, BATCH_QUOTE_TYPE);
                    for (var entry : batch.entrySet()) {
                        Quote q = toQuote(entry.getKey(), entry.getValue());
                        if (q != null) result.put(entry.getKey(), q);
                    }
                } else if (json != null) {
                    log.warn("twelvedata batch returned error: {}", json.substring(0, Math.min(json.length(), 200)));
                }
            }
            if (!result.isEmpty()) {
                cache.set(cacheKey, result, ttl);
            }
        } catch (Exception ex) {
            log.warn("twelvedata batch quote failed: {}", ex.getMessage());
        }
        return result;
    }

    private Quote toQuote(String symbol, TwelveQuoteResponse resp) {
        if (resp == null || resp.close() == null) return null;
        BigDecimal change = resp.change() != null ? resp.change() : BigDecimal.ZERO;
        BigDecimal pctChange = resp.percent_change() != null ? resp.percent_change() : BigDecimal.ZERO;
        BigDecimal prev = resp.previous_close() != null ? resp.previous_close() : BigDecimal.ZERO;
        long timestamp = resp.timestamp() != null ? resp.timestamp() : Instant.now().getEpochSecond();
        OffsetDateTime updatedAt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneOffset.UTC);
        MarketStatus status = MarketStatusResolver.resolve();
        return new Quote(symbol, resp.close(), change, pctChange,
                resp.high() != null ? resp.high() : resp.close(),
                resp.low() != null ? resp.low() : resp.close(),
                resp.open() != null ? resp.open() : resp.close(),
                prev, resp.volume() != null ? resp.volume() : 0L,
                updatedAt, status, MarketStatusResolver.priceLabel(status, updatedAt),
                null, null);
    }

    /**
     * /time_series. interval: "1min","5min","15min","30min","1h","1day","1week","1month".
     * outputsize 는 반환 bar 개수 상한 (무료 플랜 최대 5000).
     * 응답이 status=error 인 경우 빈 리스트.
     */
    public List<Candle> timeSeries(String symbol, String interval, int outputsize) {
        Duration ttl = MarketStatusResolver.resolve() == MarketStatus.OPEN
                ? TTL_SERIES_OPEN : MarketStatusResolver.durationUntilNextOpen();
        TimeSeriesResponse resp = cache.getOrLoad(
                "twelve:series:" + symbol + ":" + interval, SERIES_TYPE,
                ttl, () -> call("/time_series", uri -> uri
                        .queryParam("symbol", symbol)
                        .queryParam("interval", interval)
                        .queryParam("outputsize", outputsize)
                        .queryParam("format", "JSON")
                        .queryParam("apikey", apiKey), TimeSeriesResponse.class));
        if (resp == null || !"ok".equalsIgnoreCase(resp.status())
                || resp.values() == null || resp.values().isEmpty()) {
            return List.of();
        }
        List<Candle> out = new ArrayList<>(resp.values().size());
        for (TimeSeriesValue v : resp.values()) {
            long epochSec = parseEpochSeconds(v.datetime());
            if (epochSec < 0) {
                continue;
            }
            out.add(new Candle(
                    epochSec,
                    nullSafe(v.open()),
                    nullSafe(v.high()),
                    nullSafe(v.low()),
                    nullSafe(v.close()),
                    v.volume() == null ? 0L : v.volume().longValue()
            ));
        }
        Collections.reverse(out);
        return out;
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static long parseEpochSeconds(String datetime) {
        if (datetime == null || datetime.isBlank()) {
            return -1;
        }
        try {
            if (datetime.length() == 10) {
                return LocalDateTime.of(
                        java.time.LocalDate.parse(datetime, DATE_ONLY),
                        java.time.LocalTime.MIDNIGHT
                ).toEpochSecond(ZoneOffset.UTC);
            }
            return LocalDateTime.parse(datetime, DATE_TIME).toEpochSecond(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            log.warn("twelvedata datetime parse failed: {}", datetime);
            return -1;
        }
    }

    private <T> T call(String path, java.util.function.Function<org.springframework.web.util.UriBuilder, org.springframework.web.util.UriBuilder> uri, Class<T> type) {
        try {
            return webClient.get()
                    .uri(b -> uri.apply(b.path(path)).build())
                    .retrieve()
                    .bodyToMono(type)
                    .block(TIMEOUT);
        } catch (WebClientResponseException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
            log.warn("twelvedata {} failed: status={} body={}", path, status, ex.getResponseBodyAsString());
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                throw new BusinessException(ErrorCode.UPSTREAM_RATE_LIMIT, null, null, ex);
            }
            throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, null, null, ex);
        } catch (RuntimeException ex) {
            log.warn("twelvedata {} timeout/io: {}", path, ex.getMessage());
            throw new BusinessException(ErrorCode.UPSTREAM_TIMEOUT, null, null, ex);
        }
    }

    // ---------- Twelve Data raw response DTOs ----------
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TimeSeriesResponse(
            Meta meta,
            List<TimeSeriesValue> values,
            String status
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Meta(String symbol, String interval, String currency, String exchange) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TwelveQuoteResponse(
            String symbol,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal change,
            BigDecimal percent_change,
            BigDecimal previous_close,
            Long volume,
            Long timestamp
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TimeSeriesValue(
            String datetime,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            @JsonProperty("volume") BigDecimal volume
    ) {
    }
}
