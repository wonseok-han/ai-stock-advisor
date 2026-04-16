package com.aistockadvisor.stock.infra.client;

import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.domain.StockProfile;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Finnhub REST 어댑터. 동기 호출(virtual threads 친화).
 * Endpoints: /search, /stock/profile2, /quote.
 * Timeout: 3s (design §6.3).
 * 무료 60 req/min — 호출 빈도는 Service 캐시로 통제.
 *
 * <p>OHLCV 캔들은 Finnhub 무료에서 403 이므로 TwelveDataClient 가 담당 (기획 04-data-sources hybrid).
 */
@Component
public class FinnhubClient {

    private static final Logger log = LoggerFactory.getLogger(FinnhubClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final String apiKey;

    public FinnhubClient(FinnhubProperties props) {
        this.apiKey = props.apiKey();
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)))
                .responseTimeout(TIMEOUT);
        this.webClient = WebClient.builder()
                .baseUrl(props.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /** SymbolLookup. 빈 결과는 빈 리스트. */
    public List<SearchHit> search(String query) {
        SearchResponse resp = call("/search", uri -> uri
                .queryParam("q", query)
                .queryParam("token", apiKey), SearchResponse.class);
        return resp == null || resp.result() == null ? List.of() : resp.result();
    }

    /** CompanyProfile2. 데이터 없으면 null. */
    public StockProfile profile(String ticker) {
        ProfileResponse resp = call("/stock/profile2", uri -> uri
                .queryParam("symbol", ticker)
                .queryParam("token", apiKey), ProfileResponse.class);
        if (resp == null || resp.ticker() == null) {
            return null;
        }
        return new StockProfile(
                resp.ticker(),
                resp.name(),
                resp.exchange(),
                resp.currency(),
                resp.logo(),
                resp.finnhubIndustry(),
                resp.marketCapitalization()
        );
    }

    /** Quote. 데이터 없으면 null (timestamp 0 케이스 포함). */
    public Quote quote(String ticker) {
        QuoteResponse resp = call("/quote", uri -> uri
                .queryParam("symbol", ticker)
                .queryParam("token", apiKey), QuoteResponse.class);
        if (resp == null || resp.t() == 0L) {
            return null;
        }
        return new Quote(
                ticker,
                resp.c(),
                resp.d(),
                resp.dp(),
                resp.h(),
                resp.l(),
                resp.o(),
                resp.pc(),
                0L, // Finnhub free /quote 는 volume 미제공. Twelve Data candle 에서 보강 가능.
                OffsetDateTime.ofInstant(Instant.ofEpochSecond(resp.t()), ZoneOffset.UTC)
        );
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
            log.warn("finnhub {} failed: status={} body={}", path, status, ex.getResponseBodyAsString());
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                throw new BusinessException(ErrorCode.UPSTREAM_RATE_LIMIT, null, null, ex);
            }
            if (status != null && status.is5xxServerError()) {
                throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, null, null, ex);
            }
            throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, null, null, ex);
        } catch (RuntimeException ex) {
            log.warn("finnhub {} timeout/io: {}", path, ex.getMessage());
            throw new BusinessException(ErrorCode.UPSTREAM_TIMEOUT, null, null, ex);
        }
    }

    /**
     * /stock/symbol?exchange=US — 전체 US 심볼 목록 (동기화용, 1회/일).
     * Finnhub이 CDN으로 302 리다이렉트하며 원본 token이 CDN에 전달되면 안 됨.
     * java.net.http.HttpClient (NEVER redirect) + 수동 Location follow 로 처리.
     */
    public List<StockSymbol> stockSymbols(String exchange) {
        try {
            var client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            // 1단계: 302 Location 추출
            var req1 = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(
                            "https://finnhub.io/api/v1/stock/symbol?exchange=" + exchange + "&token=" + apiKey))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            var resp1 = client.send(req1, java.net.http.HttpResponse.BodyHandlers.discarding());

            String location;
            if (resp1.statusCode() == 302 || resp1.statusCode() == 301) {
                location = resp1.headers().firstValue("Location").orElse(null);
            } else {
                log.warn("finnhub /stock/symbol unexpected status={}", resp1.statusCode());
                return List.of();
            }

            if (location == null || location.isBlank()) {
                log.warn("finnhub /stock/symbol 302 but no Location header");
                return List.of();
            }

            // 2단계: CDN URL 직접 호출 (token 없이)
            var req2 = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(location))
                    .timeout(Duration.ofSeconds(30))
                    .GET().build();
            var resp2 = client.send(req2, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (resp2.statusCode() != 200 || resp2.body() == null || resp2.body().isBlank()) {
                log.warn("finnhub /stock/symbol CDN failed: status={} bodyLen={}",
                        resp2.statusCode(), resp2.body() == null ? 0 : resp2.body().length());
                return List.of();
            }

            log.info("finnhub /stock/symbol fetched {} bytes from CDN", resp2.body().length());
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            StockSymbol[] symbols = mapper.readValue(resp2.body(), StockSymbol[].class);
            return List.of(symbols);
        } catch (Exception ex) {
            log.warn("finnhub /stock/symbol error: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, null, null, ex);
        }
    }

    // ---------- Finnhub raw response DTOs ----------
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResponse(int count, List<SearchHit> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchHit(String symbol, String description, String displaySymbol, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockSymbol(String symbol, String description, String type, String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfileResponse(
            String ticker,
            String name,
            String exchange,
            String currency,
            String logo,
            String finnhubIndustry,
            BigDecimal marketCapitalization
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QuoteResponse(
            BigDecimal c, BigDecimal d, BigDecimal dp,
            BigDecimal h, BigDecimal l, BigDecimal o, BigDecimal pc,
            long t
    ) {
    }
}
