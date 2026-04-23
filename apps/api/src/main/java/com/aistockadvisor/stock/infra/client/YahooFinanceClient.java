package com.aistockadvisor.stock.infra.client;

import com.aistockadvisor.stock.domain.Candle;
import com.aistockadvisor.stock.domain.CompanyOverview;
import com.aistockadvisor.stock.domain.MarketStatus;
import com.aistockadvisor.stock.domain.MarketStatusResolver;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.infra.CandleEntity;
import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpCookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Yahoo Finance v8 chart API 클라이언트.
 * 무료, API key 불필요. 일봉 OHLCV + adjusted close 벌크 다운로드 전용.
 * <p>
 * 엔드포인트: https://query1.finance.yahoo.com/v8/finance/chart/{symbol}
 * 파라미터: period1, period2 (epoch), interval=1d
 * <p>
 * 참조: docs/02-design/features/phase4.5-improvements.design.md §5.1
 */
@Component
public class YahooFinanceClient {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceClient.class);
    private static final String BASE_URL = "https://query1.finance.yahoo.com";
    private static final String QUERY2_URL = "https://query2.finance.yahoo.com";
    private static final String CRUMB_INIT_URL = "https://fc.yahoo.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final String SUMMARY_MODULES = "summaryDetail,defaultKeyStatistics,assetProfile";

    private final WebClient webClient;
    private final WebClient query2Client;

    private final ReentrantLock crumbLock = new ReentrantLock();
    private volatile String crumb;
    private volatile String cookie;
    private volatile long crumbExpiresAt;

    public YahooFinanceClient() {
        this(BASE_URL);
    }

    YahooFinanceClient(String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)))
                .responseTimeout(TIMEOUT);
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", USER_AGENT)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        this.query2Client = WebClient.builder()
                .baseUrl(QUERY2_URL)
                .defaultHeader("User-Agent", USER_AGENT)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * 현재가 스냅샷. v8/finance/chart meta 필드에서 추출.
     * regularMarketPrice 가 null 이거나 0 이면 null 반환.
     */
    public Quote quote(String ticker) {
        try {
            JsonNode root = webClient.get()
                    .uri(b -> b.path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", "1m")
                            .queryParam("range", "1d")
                            .build(ticker))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(TIMEOUT);

            if (root == null) return null;
            JsonNode result = root.path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) return null;

            JsonNode meta = result.get(0).path("meta");
            BigDecimal price = tobd(meta.path("regularMarketPrice"));
            if (price == null || price.signum() == 0) return null;

            BigDecimal prevClose = tobd(meta.path("chartPreviousClose"));
            if (prevClose == null) prevClose = tobd(meta.path("previousClose"));
            BigDecimal change = prevClose != null && prevClose.signum() > 0
                    ? price.subtract(prevClose) : BigDecimal.ZERO;
            BigDecimal changePct = prevClose != null && prevClose.signum() > 0
                    ? change.divide(prevClose, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            long ts = meta.path("regularMarketTime").asLong(0);
            OffsetDateTime updatedAt = ts > 0
                    ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneOffset.UTC)
                    : OffsetDateTime.now(ZoneOffset.UTC);

            long regularStart = meta.path("currentTradingPeriod").path("regular").path("start").asLong(0);
            long regularEnd   = meta.path("currentTradingPeriod").path("regular").path("end").asLong(0);
            MarketStatus status = MarketStatusResolver.resolveByPeriod(regularStart, regularEnd);

            return new Quote(
                    ticker,
                    price,
                    change,
                    changePct,
                    tobd(meta.path("regularMarketDayHigh")),
                    tobd(meta.path("regularMarketDayLow")),
                    tobd(meta.path("regularMarketOpen")),
                    prevClose,
                    meta.path("regularMarketVolume").asLong(0),
                    updatedAt,
                    status,
                    MarketStatusResolver.priceLabel(status, updatedAt),
                    tobd(meta.path("fiftyTwoWeekHigh")),
                    tobd(meta.path("fiftyTwoWeekLow"))
            );
        } catch (Exception ex) {
            log.warn("yahoo finance quote {} failed: {}", ticker, ex.getMessage());
            return null;
        }
    }

    private static BigDecimal tobd(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        double v = node.asDouble(0);
        return v == 0 ? null : BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 인트라데이 5분봉 OHLCV. range=1d, interval=5m.
     * 실패 시 빈 리스트 반환 (예외 전파 없음 — fallback 체인 대비).
     */
    public List<Candle> fetchIntradayCandles(String ticker) {
        try {
            JsonNode root = webClient.get()
                    .uri(b -> b.path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", "5m")
                            .queryParam("range", "1d")
                            .build(ticker))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(TIMEOUT);

            return parseIntradayResponse(root);
        } catch (Exception ex) {
            log.warn("yahoo finance intraday {} failed: {}", ticker, ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Candle> parseIntradayResponse(JsonNode root) {
        if (root == null) return Collections.emptyList();

        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) return Collections.emptyList();

        JsonNode first = result.get(0);
        JsonNode timestamps = first.path("timestamp");
        JsonNode quote = first.path("indicators").path("quote");
        if (!timestamps.isArray() || !quote.isArray() || quote.isEmpty()) {
            return Collections.emptyList();
        }

        JsonNode q = quote.get(0);
        JsonNode opens = q.path("open");
        JsonNode highs = q.path("high");
        JsonNode lows = q.path("low");
        JsonNode closes = q.path("close");
        JsonNode volumes = q.path("volume");

        List<Candle> candles = new ArrayList<>(timestamps.size());
        for (int i = 0; i < timestamps.size(); i++) {
            if (timestamps.get(i).isNull() || closes.get(i).isNull()) continue;

            candles.add(new Candle(
                    timestamps.get(i).asLong(),
                    toBigDecimal(opens.get(i)),
                    toBigDecimal(highs.get(i)),
                    toBigDecimal(lows.get(i)),
                    toBigDecimal(closes.get(i)),
                    (volumes.get(i) != null && !volumes.get(i).isNull())
                            ? volumes.get(i).asLong() : 0L
            ));
        }
        return candles;
    }

    /**
     * 일봉 OHLCV + adjusted close 다운로드.
     *
     * @param ticker 종목 심볼 (예: AAPL)
     * @param from   시작일 (포함)
     * @param to     종료일 (포함)
     * @return CandleEntity 리스트 (오름차순), 실패 시 빈 리스트
     */
    public List<CandleEntity> fetchDailyCandles(String ticker, LocalDate from, LocalDate to) {
        long period1 = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long period2 = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        try {
            JsonNode root = webClient.get()
                    .uri(b -> b.path("/v8/finance/chart/{symbol}")
                            .queryParam("period1", period1)
                            .queryParam("period2", period2)
                            .queryParam("interval", "1d")
                            .queryParam("events", "div,splits")
                            .build(ticker))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(TIMEOUT);

            return parseChartResponse(ticker, root);
        } catch (Exception ex) {
            log.warn("yahoo finance {} fetch failed: {}", ticker, ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<CandleEntity> parseChartResponse(String ticker, JsonNode root) {
        if (root == null) return Collections.emptyList();

        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            log.warn("yahoo finance {} empty result", ticker);
            return Collections.emptyList();
        }

        JsonNode first = result.get(0);
        JsonNode timestamps = first.path("timestamp");
        JsonNode quote = first.path("indicators").path("quote").get(0);
        JsonNode adjCloseNode = first.path("indicators").path("adjclose");

        if (!timestamps.isArray() || !quote.isObject()) {
            return Collections.emptyList();
        }

        JsonNode opens = quote.path("open");
        JsonNode highs = quote.path("high");
        JsonNode lows = quote.path("low");
        JsonNode closes = quote.path("close");
        JsonNode volumes = quote.path("volume");

        // adjclose 는 별도 배열
        JsonNode adjCloses = adjCloseNode.isArray() && !adjCloseNode.isEmpty()
                ? adjCloseNode.get(0).path("adjclose")
                : null;

        List<CandleEntity> candles = new ArrayList<>(timestamps.size());
        for (int i = 0; i < timestamps.size(); i++) {
            if (timestamps.get(i).isNull() || opens.get(i).isNull() || closes.get(i).isNull()) {
                continue;
            }

            long epoch = timestamps.get(i).asLong();
            LocalDate date = Instant.ofEpochSecond(epoch)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();

            BigDecimal open = toBigDecimal(opens.get(i));
            BigDecimal high = toBigDecimal(highs.get(i));
            BigDecimal low = toBigDecimal(lows.get(i));
            BigDecimal close = toBigDecimal(closes.get(i));
            BigDecimal adjClose = (adjCloses != null && i < adjCloses.size() && !adjCloses.get(i).isNull())
                    ? toBigDecimal(adjCloses.get(i))
                    : close;
            long volume = (volumes.get(i) != null && !volumes.get(i).isNull())
                    ? volumes.get(i).asLong()
                    : 0L;

            candles.add(new CandleEntity(ticker, date, open, high, low, close, adjClose, volume));
        }

        return candles;
    }

    private static BigDecimal toBigDecimal(JsonNode node) {
        if (node == null || node.isNull()) return BigDecimal.ZERO;
        return BigDecimal.valueOf(node.asDouble()).setScale(4, RoundingMode.HALF_UP);
    }

    // ── crumb/cookie 인증 ──

    private void ensureCrumb() {
        if (crumb != null && System.currentTimeMillis() < crumbExpiresAt) return;
        crumbLock.lock();
        try {
            if (crumb != null && System.currentTimeMillis() < crumbExpiresAt) return;
            refreshCrumb();
        } finally {
            crumbLock.unlock();
        }
    }

    private void refreshCrumb() {
        try {
            var httpClient = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(5))
                    .cookieHandler(new java.net.CookieManager())
                    .build();

            var initReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(CRUMB_INIT_URL))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            httpClient.send(initReq, java.net.http.HttpResponse.BodyHandlers.discarding());

            var cookieStore = ((java.net.CookieManager) httpClient.cookieHandler().orElseThrow()).getCookieStore();
            StringBuilder sb = new StringBuilder();
            for (HttpCookie c : cookieStore.getCookies()) {
                if (!sb.isEmpty()) sb.append("; ");
                sb.append(c.getName()).append("=").append(c.getValue());
            }
            this.cookie = sb.toString();

            var crumbReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(QUERY2_URL + "/v1/test/getcrumb"))
                    .header("User-Agent", USER_AGENT)
                    .header("Cookie", this.cookie)
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            var crumbResp = httpClient.send(crumbReq, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (crumbResp.statusCode() == 200 && crumbResp.body() != null && !crumbResp.body().isBlank()) {
                this.crumb = crumbResp.body().trim();
                this.crumbExpiresAt = System.currentTimeMillis() + Duration.ofMinutes(20).toMillis();
                log.info("yahoo crumb refreshed successfully");
            } else {
                log.warn("yahoo crumb refresh failed: status={}", crumbResp.statusCode());
                this.crumb = null;
            }
        } catch (Exception ex) {
            log.warn("yahoo crumb refresh error: {}", ex.getMessage());
            this.crumb = null;
        }
    }

    private void invalidateCrumb() {
        this.crumb = null;
        this.crumbExpiresAt = 0;
    }

    // ── quoteSummary (기업 펀더멘털) ──

    public CompanyOverview quoteSummary(String ticker) {
        try {
            ensureCrumb();
            if (crumb == null || cookie == null) return null;

            JsonNode root = query2Client.get()
                    .uri(b -> b.path("/v10/finance/quoteSummary/{symbol}")
                            .queryParam("modules", SUMMARY_MODULES)
                            .queryParam("crumb", crumb)
                            .build(ticker))
                    .header("Cookie", cookie)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(TIMEOUT);

            if (root == null) return null;

            JsonNode result = root.path("quoteSummary").path("result");
            if (!result.isArray() || result.isEmpty()) {
                if ("401".equals(root.path("quoteSummary").path("error").path("code").asText())) {
                    invalidateCrumb();
                }
                return null;
            }

            JsonNode data = result.get(0);
            return parseQuoteSummary(data);
        } catch (Exception ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("401")) invalidateCrumb();
            log.warn("yahoo quoteSummary {} failed: {}", ticker, msg);
            return null;
        }
    }

    private CompanyOverview parseQuoteSummary(JsonNode data) {
        JsonNode summary = data.path("summaryDetail");
        JsonNode keyStats = data.path("defaultKeyStatistics");
        JsonNode profile = data.path("assetProfile");

        return new CompanyOverview(
                textOrNull(profile.path("sector")),
                textOrNull(profile.path("industry")),
                rawNum(summary.path("marketCap")),
                rawNum(summary.path("trailingPE")),
                rawNum(keyStats.path("trailingEps")),
                rawNum(summary.path("dividendRate")),
                rawNum(summary.path("beta")),
                rawNum(summary.path("fiftyTwoWeekHigh")),
                rawNum(summary.path("fiftyTwoWeekLow")),
                textOrNull(profile.path("longBusinessSummary")),
                intOrNull(profile.path("fullTimeEmployees")),
                textOrNull(profile.path("website")),
                null
        );
    }

    private static BigDecimal rawNum(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;
        JsonNode raw = node.path("raw");
        if (raw.isMissingNode() || raw.isNull()) return null;
        double v = raw.asDouble(0);
        return v == 0 ? null : BigDecimal.valueOf(v);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String v = node.asText();
        return (v == null || v.isBlank()) ? null : v;
    }

    private static Integer intOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        int v = node.asInt(0);
        return v == 0 ? null : v;
    }
}
