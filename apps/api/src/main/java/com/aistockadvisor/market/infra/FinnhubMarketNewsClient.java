package com.aistockadvisor.market.infra;

import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import com.aistockadvisor.news.infra.FinnhubNewsClient.CompanyNews;
import com.aistockadvisor.stock.infra.client.FinnhubProperties;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Finnhub /news?category=general 어댑터.
 * 시장 일반 뉴스 조회. CompanyNews record 재사용 (동일 JSON 구조).
 * 참조: docs/02-design/features/market-dashboard.design.md §5.2
 */
@Component
public class FinnhubMarketNewsClient {

    private static final Logger log = LoggerFactory.getLogger(FinnhubMarketNewsClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_ITEMS = 10;

    private final WebClient webClient;
    private final String apiKey;

    public FinnhubMarketNewsClient(FinnhubProperties props) {
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

    /**
     * 시장 일반 뉴스 최대 10건.
     * Finnhub /news?category=general 은 최신순 정렬 반환.
     */
    public List<CompanyNews> fetchGeneralNews() {
        try {
            List<CompanyNews> raw = webClient.get()
                    .uri(b -> b.path("/news")
                            .queryParam("category", "general")
                            .queryParam("minId", 0)
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
            log.warn("finnhub /news general failed status={} body={}",
                    status, ex.getResponseBodyAsString());
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                throw new BusinessException(ErrorCode.UPSTREAM_RATE_LIMIT, null, null, ex);
            }
            throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, null, null, ex);
        } catch (RuntimeException ex) {
            log.warn("finnhub /news general timeout reason={}", ex.getMessage());
            throw new BusinessException(ErrorCode.UPSTREAM_TIMEOUT, null, null, ex);
        }
    }
}
