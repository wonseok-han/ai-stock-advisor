package com.aistockadvisor.market.infra;

import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Financial Modeling Prep REST 어댑터.
 * 무료 250 req/day. Market movers (gainers/losers) 전담.
 */
@Component
public class FmpClient {

    private static final Logger log = LoggerFactory.getLogger(FmpClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final String apiKey;

    public FmpClient(FmpProperties props) {
        this.apiKey = props.apiKey();
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

    public List<FmpMover> gainers() {
        return fetchMovers("/biggest-gainers");
    }

    public List<FmpMover> losers() {
        return fetchMovers("/biggest-losers");
    }

    private List<FmpMover> fetchMovers(String path) {
        try {
            FmpMover[] resp = webClient.get()
                    .uri(b -> b.path(path)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(FmpMover[].class)
                    .block(TIMEOUT);
            return resp == null ? List.of() : List.of(resp);
        } catch (WebClientResponseException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
            log.warn("fmp {} failed: status={} body={}", path, status, ex.getResponseBodyAsString());
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                throw new BusinessException(ErrorCode.UPSTREAM_RATE_LIMIT, null, null, ex);
            }
            throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, null, null, ex);
        } catch (RuntimeException ex) {
            log.warn("fmp {} timeout/io: {}", path, ex.getMessage());
            throw new BusinessException(ErrorCode.UPSTREAM_TIMEOUT, null, null, ex);
        }
    }

    public List<FmpSectorPerformance> sectorPerformance() {
        try {
            FmpSectorPerformance[] resp = webClient.get()
                    .uri(b -> b.path("/sector-performance")
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(FmpSectorPerformance[].class)
                    .block(TIMEOUT);
            return resp == null ? List.of() : List.of(resp);
        } catch (Exception ex) {
            log.warn("fmp sector-performance failed: {}", ex.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FmpSectorPerformance(
            String sector,
            @JsonProperty("changesPercentage") Double changePercent
    ) {
    }

    public FmpProfile companyProfile(String ticker) {
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FmpMover(
            String symbol,
            String name,
            BigDecimal price,
            BigDecimal change,
            BigDecimal changesPercentage,
            String exchange
    ) {
    }

    public FmpRatiosTtm ratiosTtm(String ticker) {
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
