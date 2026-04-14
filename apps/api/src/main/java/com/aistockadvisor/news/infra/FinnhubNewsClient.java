package com.aistockadvisor.news.infra;

import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import com.aistockadvisor.stock.infra.client.FinnhubProperties;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Finnhub /company-news 어댑터.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §4 (GET /news).
 *
 * <p>Free tier: 60 req/min — 호출 빈도는 NewsService 의 DB 24h 캐시로 통제.
 * Endpoint: GET /company-news?symbol={ticker}&from={YYYY-MM-DD}&to={YYYY-MM-DD}
 */
@Component
public class FinnhubNewsClient {

    private static final Logger log = LoggerFactory.getLogger(FinnhubNewsClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int LOOKBACK_DAYS = 3;
    private static final int MAX_ITEMS = 5;

    private final WebClient webClient;
    private final String apiKey;

    public FinnhubNewsClient(FinnhubProperties props) {
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

    /** 최근 3일 뉴스 최대 5건. 시간 역순 정렬 후 반환. */
    public List<CompanyNews> fetchRecent(String ticker) {
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = to.minusDays(LOOKBACK_DAYS);
        try {
            List<CompanyNews> raw = webClient.get()
                    .uri(b -> b.path("/company-news")
                            .queryParam("symbol", ticker)
                            .queryParam("from", DATE.format(from))
                            .queryParam("to", DATE.format(to))
                            .queryParam("token", apiKey)
                            .build())
                    .retrieve()
                    .bodyToFlux(CompanyNews.class)
                    .collectList()
                    .block(TIMEOUT);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            return raw.stream()
                    .filter(c -> c.url() != null && !c.url().isBlank())
                    .filter(c -> c.headline() != null && !c.headline().isBlank())
                    .sorted((a, b) -> Long.compare(b.datetime(), a.datetime()))
                    .limit(MAX_ITEMS)
                    .toList();
        } catch (WebClientResponseException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
            log.warn("finnhub /company-news failed ticker={} status={} body={}",
                    ticker, status, ex.getResponseBodyAsString());
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                throw new BusinessException(ErrorCode.UPSTREAM_RATE_LIMIT, null, null, ex);
            }
            throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, null, null, ex);
        } catch (RuntimeException ex) {
            log.warn("finnhub /company-news timeout ticker={} reason={}", ticker, ex.getMessage());
            throw new BusinessException(ErrorCode.UPSTREAM_TIMEOUT, null, null, ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompanyNews(
            long id,
            long datetime,
            String headline,
            String source,
            String summary,
            String url,
            String category,
            String related
    ) {
    }
}
