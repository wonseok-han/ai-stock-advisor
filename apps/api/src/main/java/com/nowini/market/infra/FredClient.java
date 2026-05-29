package com.nowini.market.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * FRED REST 어댑터. 시리즈 최신 관측값 조회 전담.
 * <p>
 * 엔드포인트: {@code GET /series/observations?series_id=&api_key=&file_type=json&sort_order=desc&limit=1}
 * 결측치는 {@code "."} 로 오므로 null 처리. api-key 없으면 비활성(null 반환).
 */
@Component
public class FredClient {

    private static final Logger log = LoggerFactory.getLogger(FredClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final FredProperties props;

    public FredClient(FredProperties props) {
        this.props = props;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)))
                .responseTimeout(TIMEOUT);
        this.webClient = WebClient.builder()
                .baseUrl(props.baseUrlOrDefault())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public boolean isEnabled() {
        return props.enabled();
    }

    /** 시리즈 최신 관측값. 비활성/실패/결측 시 null. */
    public FredObservation latestValue(String seriesId) {
        if (!props.enabled()) return null;
        try {
            FredResponse resp = webClient.get()
                    .uri(b -> b.path("/series/observations")
                            .queryParam("series_id", seriesId)
                            .queryParam("api_key", props.apiKey())
                            .queryParam("file_type", "json")
                            .queryParam("sort_order", "desc")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(FredResponse.class)
                    .block(TIMEOUT);
            if (resp == null || resp.observations() == null || resp.observations().isEmpty()) return null;
            Obs o = resp.observations().get(0);
            if (o.value() == null || ".".equals(o.value())) return null;
            return new FredObservation(o.date(), Double.parseDouble(o.value()));
        } catch (Exception ex) {
            log.warn("fred {} fetch failed: {}", seriesId, ex.getMessage());
            return null;
        }
    }

    /** 최신순 다건 관측값 (결측 제외). 200일 이동평균 등 계산용. 비활성/실패 시 빈 리스트. */
    public List<FredObservation> latestSeries(String seriesId, int limit) {
        if (!props.enabled()) return List.of();
        try {
            FredResponse resp = webClient.get()
                    .uri(b -> b.path("/series/observations")
                            .queryParam("series_id", seriesId)
                            .queryParam("api_key", props.apiKey())
                            .queryParam("file_type", "json")
                            .queryParam("sort_order", "desc")
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .bodyToMono(FredResponse.class)
                    .block(TIMEOUT);
            if (resp == null || resp.observations() == null) return List.of();
            List<FredObservation> out = new ArrayList<>();
            for (Obs o : resp.observations()) {
                if (o.value() == null || ".".equals(o.value())) continue;
                out.add(new FredObservation(o.date(), Double.parseDouble(o.value())));
            }
            return out;
        } catch (Exception ex) {
            log.warn("fred series {} fetch failed: {}", seriesId, ex.getMessage());
            return List.of();
        }
    }

    public record FredObservation(String date, double value) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FredResponse(List<Obs> observations) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Obs(String date, String value) {}
}
