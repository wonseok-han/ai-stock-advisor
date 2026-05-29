package com.nowini.market.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * CNN Fear & Greed Index 클라이언트 (무료, API key 불필요).
 * <p>
 * 엔드포인트: {@code GET https://production.dataviz.cnn.io/index/fearandgreed/graphdata}
 * (User-Agent + Referer 헤더 필요). 현재 score/rating + 1주/1달/1년 전 비교값 제공.
 */
@Component
public class CnnFearGreedClient {

    private static final Logger log = LoggerFactory.getLogger(CnnFearGreedClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final String REFERER = "https://www.cnn.com/markets/fear-and-greed";

    private final WebClient webClient;

    public CnnFearGreedClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)))
                .responseTimeout(TIMEOUT);
        this.webClient = WebClient.builder()
                .baseUrl("https://production.dataviz.cnn.io")
                .defaultHeader("User-Agent", UA)
                .defaultHeader("Referer", REFERER)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /** 현재 Fear & Greed 스냅샷. 실패 시 null. */
    public FearGreedSnapshot fetch() {
        try {
            CnnResponse resp = webClient.get()
                    .uri("/index/fearandgreed/graphdata")
                    .retrieve()
                    .bodyToMono(CnnResponse.class)
                    .block(TIMEOUT);
            if (resp == null || resp.fearAndGreed() == null) return null;
            FgData fg = resp.fearAndGreed();
            return new FearGreedSnapshot(fg.score(), fg.rating(),
                    fg.previous1Week(), fg.previous1Month(), fg.previous1Year());
        } catch (Exception ex) {
            log.warn("cnn fear&greed fetch failed: {}", ex.getMessage());
            return null;
        }
    }

    public record FearGreedSnapshot(double score, String rating,
                                    Double prev1Week, Double prev1Month, Double prev1Year) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CnnResponse(@JsonProperty("fear_and_greed") FgData fearAndGreed) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FgData(
            double score,
            String rating,
            @JsonProperty("previous_1_week") Double previous1Week,
            @JsonProperty("previous_1_month") Double previous1Month,
            @JsonProperty("previous_1_year") Double previous1Year
    ) {}
}
