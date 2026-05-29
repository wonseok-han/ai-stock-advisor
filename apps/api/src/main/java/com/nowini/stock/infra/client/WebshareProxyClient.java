package com.nowini.stock.infra.client;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Webshare 프록시 리스트 API 클라이언트.
 * <p>
 * List API({@code GET /api/v2/proxy/list/})를 호출해 유효한 프록시를
 * {@code http://username:password@ip:port} 형태의 리스트로 반환한다.
 * <p>
 * {@code WEBSHARE_API_KEY}가 없으면 비활성화되어 빈 리스트를 반환한다
 * (이 경우 Yahoo에 프록시 없이 직접 호출).
 * <p>
 * 참조: https://apidocs.webshare.io/proxy-list/list
 */
@Component
public class WebshareProxyClient {

    private static final Logger log = LoggerFactory.getLogger(WebshareProxyClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_BASE_URL = "https://proxy.webshare.io";
    private static final int MAX_PAGES = 20;

    private final WebshareProperties props;
    private final WebClient webClient;

    public WebshareProxyClient(WebshareProperties props) {
        this.props = props;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)))
                .responseTimeout(TIMEOUT);
        String base = (props.baseUrl() != null && !props.baseUrl().isBlank())
                ? props.baseUrl() : DEFAULT_BASE_URL;
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(base)
                .clientConnector(new ReactorClientHttpConnector(httpClient));
        if (props.enabled()) {
            builder.defaultHeader("Authorization", "Token " + props.apiKey());
        }
        this.webClient = builder.build();
    }

    public boolean isEnabled() {
        return props.enabled();
    }

    /**
     * 전체 프록시 리스트를 페이지네이션으로 수집해 proxy URL 리스트로 반환한다.
     * 실패 시 빈 리스트를 반환한다 (호출측에서 기존 풀을 유지하도록).
     */
    public List<String> fetchProxyUrls() {
        if (!props.enabled()) return List.of();

        String mode = (props.mode() != null && !props.mode().isBlank()) ? props.mode() : "direct";
        int pageSize = (props.pageSize() != null && props.pageSize() > 0) ? props.pageSize() : 100;
        String countryCodes = props.countryCodes();

        List<String> out = new ArrayList<>();
        try {
            for (int page = 1; page <= MAX_PAGES; page++) {
                final int p = page;
                WebshareListResponse resp = webClient.get()
                        .uri(uri -> {
                            var b = uri.path("/api/v2/proxy/list/")
                                    .queryParam("mode", mode)
                                    .queryParam("valid", "true")
                                    .queryParam("page", p)
                                    .queryParam("page_size", pageSize);
                            if (countryCodes != null && !countryCodes.isBlank()) {
                                b.queryParam("country_code__in", countryCodes.trim());
                            }
                            return b.build();
                        })
                        .retrieve()
                        .bodyToMono(WebshareListResponse.class)
                        .block(TIMEOUT);

                if (resp == null || resp.results() == null || resp.results().isEmpty()) break;
                for (WebshareProxy proxy : resp.results()) {
                    if (!proxy.valid()) continue;
                    out.add("http://" + proxy.username() + ":" + proxy.password()
                            + "@" + proxy.proxyAddress() + ":" + proxy.port());
                }
                if (resp.next() == null || resp.next().isBlank()) break;
            }
            log.info("webshare: fetched {} valid proxies (mode={})", out.size(), mode);
            return out;
        } catch (Exception ex) {
            log.warn("webshare proxy list fetch failed: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Gemini 위치 차단 우회용 US 프록시 1개를 반환한다 (없거나 비활성이면 null).
     * Gemini는 IP 위치만 검사하므로 US datacenter 프록시 1개로 충분하다.
     */
    public String fetchUsProxyUrl() {
        if (!props.enabled()) return null;
        try {
            WebshareListResponse resp = webClient.get()
                    .uri(uri -> uri.path("/api/v2/proxy/list/")
                            .queryParam("mode", "direct")
                            .queryParam("valid", "true")
                            .queryParam("country_code__in", "US")
                            .queryParam("page", 1)
                            .queryParam("page_size", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(WebshareListResponse.class)
                    .block(TIMEOUT);
            if (resp == null || resp.results() == null || resp.results().isEmpty()) return null;
            WebshareProxy p = resp.results().get(0);
            if (!p.valid()) return null;
            return "http://" + p.username() + ":" + p.password()
                    + "@" + p.proxyAddress() + ":" + p.port();
        } catch (Exception ex) {
            log.warn("webshare US proxy fetch failed: {}", ex.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WebshareListResponse(
            int count,
            String next,
            List<WebshareProxy> results
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WebshareProxy(
            @JsonProperty("proxy_address") String proxyAddress,
            int port,
            String username,
            String password,
            boolean valid
    ) {}
}
