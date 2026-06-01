package com.nowini.market.infra;

import com.nowini.cache.RedisCacheAdapter;
import com.nowini.common.error.BusinessException;
import com.nowini.common.error.ErrorCode;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Financial Modeling Prep REST 어댑터.
 * 무료 250 req/day (계정 전체 공유). 시세(지수·VIX·귀금속) · 섹터 퍼포먼스 ·
 * 기업 프로필 · 재무비율 담당. 무료는 콤마 batch 불가 → 심볼당 1요청.
 */
@Component
public class FmpClient {

    private static final Logger log = LoggerFactory.getLogger(FmpClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final Duration TTL_MARKET_OPEN  = Duration.ofMinutes(15);
    private static final Duration TTL_TICKER_OPEN  = Duration.ofHours(24);
    private static final Duration TTL_TICKER_CLOSED = Duration.ofHours(24);

    private static final TypeReference<Quote> QUOTE_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<FmpSectorPerformance>> SECTORS_TYPE = new TypeReference<>() {};
    private static final TypeReference<FmpProfile> PROFILE_TYPE = new TypeReference<>() {};
    private static final TypeReference<FmpRatiosTtm> RATIOS_TYPE = new TypeReference<>() {};
    private static final TypeReference<FmpGradesConsensus> GRADES_TYPE = new TypeReference<>() {};
    private static final TypeReference<FmpPriceTargetConsensus> PRICE_TARGET_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<FmpAnalystEstimate>> ESTIMATES_TYPE = new TypeReference<>() {};

    private final WebClient webClient;
    private final String apiKey;
    private final RedisCacheAdapter cache;

    public FmpClient(FmpProperties props, RedisCacheAdapter cache) {
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

    private Duration marketTtl() {
        return MarketStatusResolver.resolve() == MarketStatus.OPEN
                ? TTL_MARKET_OPEN : MarketStatusResolver.durationUntilNextOpen();
    }

    /**
     * /quote. 단일 심볼 시세. 지수(^GSPC·^IXIC·^DJI·^RUT)·VIX(^VIX)·귀금속(GCUSD·SIUSD)을
     * FMP 무료로 커버 — 프록시 불필요. 무료에서 막힌 심볼(에너지·환율·금리·선물·콤마 batch)은
     * "Premium..." 문자열을 200으로 반환하므로, 디코딩 실패 → null → 호출측 폴백으로 처리된다.
     * 데이터 없거나 실패 시 null.
     */
    public Quote quote(String symbol) {
        if (symbol == null || symbol.isBlank()) return null;
        return cache.getOrLoad("fmp:quote:" + symbol, QUOTE_TYPE, marketTtl(),
                () -> fetchQuote(symbol));
    }

    private Quote fetchQuote(String symbol) {
        try {
            FmpQuote[] resp = webClient.get()
                    .uri(b -> b.path("/quote")
                            .queryParam("symbol", symbol)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(FmpQuote[].class)
                    .block(TIMEOUT);
            return (resp != null && resp.length > 0) ? toQuote(resp[0]) : null;
        } catch (Exception ex) {
            log.warn("fmp quote {} failed: {}", symbol, ex.getMessage());
            return null;
        }
    }

    private Quote toQuote(FmpQuote q) {
        if (q == null || q.price() == null) return null;
        BigDecimal price = q.price();
        BigDecimal change = q.change() != null ? q.change() : BigDecimal.ZERO;
        BigDecimal pct = q.changePercentage() != null ? q.changePercentage() : BigDecimal.ZERO;
        BigDecimal prev = q.previousClose() != null ? q.previousClose() : BigDecimal.ZERO;
        long ts = q.timestamp() != null ? q.timestamp() : Instant.now().getEpochSecond();
        OffsetDateTime updatedAt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneOffset.UTC);
        MarketStatus status = MarketStatusResolver.resolve();
        return new Quote(q.symbol(), price, change, pct,
                q.dayHigh() != null ? q.dayHigh() : price,
                q.dayLow() != null ? q.dayLow() : price,
                q.open() != null ? q.open() : price,
                prev, q.volume() != null ? q.volume() : 0L,
                updatedAt, status, MarketStatusResolver.priceLabel(status, updatedAt),
                null, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FmpQuote(
            String symbol,
            BigDecimal price,
            BigDecimal change,
            BigDecimal changePercentage,
            BigDecimal dayHigh,
            BigDecimal dayLow,
            BigDecimal open,
            BigDecimal previousClose,
            Long volume,
            Long timestamp
    ) {
    }

    public List<FmpSectorPerformance> sectorPerformance() {
        return cache.getOrLoad("fmp:sectors", SECTORS_TYPE, marketTtl(),
                this::fetchSectorPerformance);
    }

    /**
     * FMP stable sector-performance-snapshot 조회.
     * <p>
     * 구 {@code /sector-performance}는 404(폐기)되어 {@code /sector-performance-snapshot}으로 전환.
     * 이 엔드포인트는 {@code date} 파라미터가 필수이고 거래소(NASDAQ/NYSE/AMEX)별로 sector가
     * 중복 반환되므로, sector별 averageChange를 평균내어 기존 {@link FmpSectorPerformance} 형태로 변환한다.
     * 주말/휴일 대비로 ET 기준 최근 5일을 역순으로 시도해 첫 유효 응답을 사용한다.
     */
    private List<FmpSectorPerformance> fetchSectorPerformance() {
        ZoneId et = ZoneId.of("America/New_York");
        for (int i = 0; i < 5; i++) {
            final String date = LocalDate.now(et).minusDays(i).toString();
            try {
                FmpSectorSnapshot[] resp = webClient.get()
                        .uri(b -> b.path("/sector-performance-snapshot")
                                .queryParam("date", date)
                                .queryParam("apikey", apiKey)
                                .build())
                        .retrieve()
                        .bodyToMono(FmpSectorSnapshot[].class)
                        .block(TIMEOUT);
                if (resp == null || resp.length == 0) continue;

                // 거래소별 중복을 sector별 평균으로 집계
                Map<String, List<Double>> bySector = new LinkedHashMap<>();
                for (FmpSectorSnapshot s : resp) {
                    if (s.sector() == null || s.averageChange() == null) continue;
                    bySector.computeIfAbsent(s.sector(), k -> new ArrayList<>()).add(s.averageChange());
                }
                List<FmpSectorPerformance> out = new ArrayList<>();
                for (Map.Entry<String, List<Double>> e : bySector.entrySet()) {
                    double avg = e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    out.add(new FmpSectorPerformance(e.getKey(), avg));
                }
                return out;
            } catch (Exception ex) {
                log.warn("fmp sector snapshot {} failed: {}", date, ex.getMessage());
            }
        }
        log.warn("fmp sector snapshot: 최근 5일 내 유효 데이터 없음");
        return List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FmpSectorPerformance(
            String sector,
            @JsonProperty("changesPercentage") Double changePercent
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FmpSectorSnapshot(
            String sector,
            String exchange,
            Double averageChange
    ) {
    }

    public FmpProfile companyProfile(String ticker) {
        return cache.getOrLoad("fmp:profile:" + ticker, PROFILE_TYPE, TTL_TICKER_OPEN,
                () -> fetchCompanyProfile(ticker));
    }

    private FmpProfile fetchCompanyProfile(String ticker) {
        try {
            FmpProfile[] resp = webClient.get()
                    .uri(b -> b.path("/profile")
                            .queryParam("symbol", ticker)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(FmpProfile[].class)
                    .block(TIMEOUT);
            return (resp != null && resp.length > 0) ? resp[0] : null;
        } catch (WebClientResponseException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
            log.warn("fmp profile {} failed: status={}", ticker, status);
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                throw new BusinessException(ErrorCode.UPSTREAM_RATE_LIMIT, null, null, ex);
            }
            throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, null, null, ex);
        } catch (RuntimeException ex) {
            log.warn("fmp profile {} timeout/io: {}", ticker, ex.getMessage());
            throw new BusinessException(ErrorCode.UPSTREAM_TIMEOUT, null, null, ex);
        }
    }

    public FmpRatiosTtm ratiosTtm(String ticker) {
        return cache.getOrLoad("fmp:ratios:" + ticker, RATIOS_TYPE, TTL_TICKER_OPEN,
                () -> fetchRatiosTtm(ticker));
    }

    private FmpRatiosTtm fetchRatiosTtm(String ticker) {
        try {
            FmpRatiosTtm[] resp = webClient.get()
                    .uri(b -> b.path("/ratios-ttm")
                            .queryParam("symbol", ticker)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(FmpRatiosTtm[].class)
                    .block(TIMEOUT);
            return (resp != null && resp.length > 0) ? resp[0] : null;
        } catch (Exception ex) {
            log.warn("fmp ratios-ttm {} failed: {}", ticker, ex.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FmpProfile(
            String symbol,
            String companyName,
            String sector,
            String industry,
            @JsonProperty("marketCap") Long mktCap,
            Double beta,
            @JsonProperty("lastDividend") Double lastDiv,
            String description,
            Integer fullTimeEmployees,
            String website,
            String ipoDate
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FmpRatiosTtm(
            @JsonProperty("priceToEarningsRatioTTM") Double peRatio,
            @JsonProperty("netIncomePerShareTTM") Double eps,
            @JsonProperty("dividendPerShareTTM") Double dividendPerShare
    ) {
    }

    // ── Analyst Estimates ──

    public FmpGradesConsensus gradesConsensus(String ticker) {
        return cache.getOrLoad("fmp:grades:" + ticker, GRADES_TYPE, TTL_TICKER_OPEN,
                () -> fetchGradesConsensus(ticker));
    }

    private FmpGradesConsensus fetchGradesConsensus(String ticker) {
        try {
            FmpGradesConsensus[] resp = webClient.get()
                    .uri(b -> b.path("/grades-consensus")
                            .queryParam("symbol", ticker)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(FmpGradesConsensus[].class)
                    .block(TIMEOUT);
            return (resp != null && resp.length > 0) ? resp[0] : null;
        } catch (Exception ex) {
            log.warn("fmp grades-consensus {} failed: {}", ticker, ex.getMessage());
            return null;
        }
    }

    public FmpPriceTargetConsensus priceTargetConsensus(String ticker) {
        return cache.getOrLoad("fmp:price-target:" + ticker, PRICE_TARGET_TYPE, TTL_TICKER_OPEN,
                () -> fetchPriceTargetConsensus(ticker));
    }

    private FmpPriceTargetConsensus fetchPriceTargetConsensus(String ticker) {
        try {
            FmpPriceTargetConsensus[] resp = webClient.get()
                    .uri(b -> b.path("/price-target-consensus")
                            .queryParam("symbol", ticker)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(FmpPriceTargetConsensus[].class)
                    .block(TIMEOUT);
            return (resp != null && resp.length > 0) ? resp[0] : null;
        } catch (Exception ex) {
            log.warn("fmp price-target-consensus {} failed: {}", ticker, ex.getMessage());
            return null;
        }
    }

    public List<FmpAnalystEstimate> analystEstimates(String ticker) {
        return cache.getOrLoad("fmp:estimates:" + ticker, ESTIMATES_TYPE, TTL_TICKER_OPEN,
                () -> fetchAnalystEstimates(ticker));
    }

    private List<FmpAnalystEstimate> fetchAnalystEstimates(String ticker) {
        try {
            FmpAnalystEstimate[] resp = webClient.get()
                    .uri(b -> b.path("/analyst-estimates")
                            .queryParam("symbol", ticker)
                            .queryParam("period", "quarter")
                            .queryParam("limit", "4")
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(FmpAnalystEstimate[].class)
                    .block(TIMEOUT);
            return resp == null ? List.of() : List.of(resp);
        } catch (Exception ex) {
            log.warn("fmp analyst-estimates {} failed: {}", ticker, ex.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FmpGradesConsensus(
            String symbol,
            Integer strongBuy,
            Integer buy,
            Integer hold,
            Integer sell,
            Integer strongSell,
            String consensus
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FmpPriceTargetConsensus(
            BigDecimal targetHigh,
            BigDecimal targetLow,
            BigDecimal targetConsensus,
            BigDecimal targetMedian
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FmpAnalystEstimate(
            String symbol,
            String date,
            BigDecimal estimatedEpsAvg,
            BigDecimal estimatedEpsHigh,
            BigDecimal estimatedEpsLow,
            Integer numberAnalystsEstimatedEps
    ) {
    }
}
