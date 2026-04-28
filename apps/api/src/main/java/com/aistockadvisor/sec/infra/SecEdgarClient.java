package com.aistockadvisor.sec.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SEC EDGAR 공개 API 클라이언트. API key 불필요.
 * Rate limit: 10 req/s — 캐시 계층으로 반복 호출 차단.
 * User-Agent 헤더 필수 (EDGAR 정책).
 */
@Component
public class SecEdgarClient {

    private static final Logger log = LoggerFactory.getLogger(SecEdgarClient.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String DATA_BASE = "https://data.sec.gov";
    private static final String TICKERS_URL = "https://www.sec.gov/files/company_tickers.json";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final WebClient dataClient;
    private final WebClient tickersClient;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, String> tickerCikMap = new ConcurrentHashMap<>();
    private final AtomicBoolean tickersLoaded = new AtomicBoolean(false);

    public SecEdgarClient(ObjectMapper objectMapper,
                          @org.springframework.beans.factory.annotation.Value("${app.resend.contact-email:}") String contactEmail) {
        String userAgent = "Nowini/1.0" + (contactEmail.isBlank() ? "" : " " + contactEmail);
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)))
                .responseTimeout(TIMEOUT);
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

        // company_tickers.json (~수 MB) 및 companyfacts (~수 MB) 를 수용하기 위해 10MB 로 확장
        ExchangeStrategies largeBuffer = ExchangeStrategies.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        this.dataClient = WebClient.builder()
                .baseUrl(DATA_BASE)
                .defaultHeader("User-Agent", userAgent)
                .defaultHeader("Accept", "application/json")
                .clientConnector(connector)
                .exchangeStrategies(largeBuffer)
                .build();

        this.tickersClient = WebClient.builder()
                .defaultHeader("User-Agent", userAgent)
                .defaultHeader("Accept", "application/json")
                .clientConnector(connector)
                .exchangeStrategies(largeBuffer)
                .build();
    }

    /**
     * Ticker → CIK (10자리 제로패딩 문자열).
     * company_tickers.json 전체를 인메모리에 lazy 로드하여 이후 조회는 O(1).
     */
    public Optional<String> getCik(String ticker) {
        ensureTickersLoaded();
        String cik = tickerCikMap.get(ticker.toUpperCase());
        return Optional.ofNullable(cik);
    }

    /**
     * 기업 공시 목록 (submissions JSON 원본).
     * filings.recent 하위의 form, filingDate, items 배열 포함.
     */
    public Map<String, Object> getSubmissions(String cik) {
        try {
            String json = dataClient.get()
                    .uri("/submissions/CIK{cik}.json", cik)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            if (json == null) return Map.of();
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (WebClientResponseException ex) {
            log.warn("edgar submissions failed cik={} status={}", cik, ex.getStatusCode());
            if (ex.getStatusCode().value() == 429) {
                sleepQuietly(1000);
                return retrySubmissions(cik);
            }
            return Map.of();
        } catch (Exception ex) {
            log.warn("edgar submissions error cik={} reason={}", cik, ex.getMessage());
            return Map.of();
        }
    }

    /**
     * XBRL 재무 팩트 (companyfacts JSON 원본).
     * facts.us-gaap.Revenues / NetIncomeLoss 포함.
     */
    public Map<String, Object> getCompanyFacts(String cik) {
        try {
            String json = dataClient.get()
                    .uri("/api/xbrl/companyfacts/CIK{cik}.json", cik)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            if (json == null) return Map.of();
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (WebClientResponseException ex) {
            log.warn("edgar companyfacts failed cik={} status={}", cik, ex.getStatusCode());
            if (ex.getStatusCode().value() == 429) {
                sleepQuietly(1000);
                return retryCompanyFacts(cik);
            }
            return Map.of();
        } catch (Exception ex) {
            log.warn("edgar companyfacts error cik={} reason={}", cik, ex.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private void ensureTickersLoaded() {
        if (tickersLoaded.get()) return;
        synchronized (this) {
            if (tickersLoaded.get()) return;
            try {
                String json = tickersClient.get()
                        .uri(TICKERS_URL)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(TIMEOUT);
                if (json == null) return;
                Map<String, Object> raw = objectMapper.readValue(json, MAP_TYPE);
                raw.forEach((k, v) -> {
                    if (v instanceof Map<?, ?> entry) {
                        Object tickerObj = entry.get("ticker");
                        Object cikObj = entry.get("cik_str");
                        if (tickerObj != null && cikObj != null) {
                            String cikPadded = String.format("%010d", ((Number) cikObj).longValue());
                            tickerCikMap.put(tickerObj.toString().toUpperCase(), cikPadded);
                        }
                    }
                });
                tickersLoaded.set(true);
                log.info("edgar tickers loaded: {} entries", tickerCikMap.size());
            } catch (Exception ex) {
                log.warn("edgar tickers load failed: {}", ex.getMessage());
            }
        }
    }

    /**
     * SEC Archives에서 공시 문서 원문(HTML)을 가져온다.
     * @param url 전체 URL (예: https://www.sec.gov/Archives/edgar/data/...)
     * @return HTML 문자열, 실패 시 null
     */
    public String fetchDocumentText(String url) {
        try {
            return tickersClient.get()
                    .uri(url)
                    .header("Accept", "*/*")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
        } catch (Exception ex) {
            log.debug("edgar document fetch failed url={} reason={}", url, ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> retrySubmissions(String cik) {
        try {
            String json = dataClient.get()
                    .uri("/submissions/CIK{cik}.json", cik)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            if (json == null) return Map.of();
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            log.warn("edgar submissions retry failed cik={}", cik);
            return Map.of();
        }
    }

    private Map<String, Object> retryCompanyFacts(String cik) {
        try {
            String json = dataClient.get()
                    .uri("/api/xbrl/companyfacts/CIK{cik}.json", cik)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            if (json == null) return Map.of();
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            log.warn("edgar companyfacts retry failed cik={}", cik);
            return Map.of();
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 테스트용 CIK 맵 초기화 (인메모리 상태 리셋). */
    void resetTickerCache() {
        tickerCikMap.clear();
        tickersLoaded.set(false);
    }

    /** 테스트용 CIK 직접 주입. */
    void putCik(String ticker, String cik) {
        tickerCikMap.put(ticker.toUpperCase(), cik);
        tickersLoaded.set(true);
    }

    Map<String, String> getTickerCikMap() {
        return Collections.unmodifiableMap(tickerCikMap);
    }
}
