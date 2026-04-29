package com.nowini.stock.infra.client;

import com.nowini.cache.RedisCacheAdapter;
import com.nowini.stock.domain.AnalystEstimates;
import com.nowini.stock.domain.Candle;
import com.nowini.stock.domain.CompanyOverview;
import com.nowini.stock.domain.MarketStatus;
import com.nowini.stock.domain.MarketStatusResolver;
import com.nowini.stock.domain.Quote;
import com.nowini.stock.infra.CandleEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import reactor.netty.transport.ProxyProvider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final String[] CHART_HOSTS = {BASE_URL, QUERY2_URL};
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
    };
    private static final String DEFAULT_UA = USER_AGENTS[0];
    private static final String ALL_MODULES = "summaryDetail,defaultKeyStatistics,assetProfile,financialData,recommendationTrend,earningsHistory";
    private static final Duration SUMMARY_CACHE_TTL = Duration.ofHours(24);
    private static final Duration TTL_CHART_OPEN = Duration.ofMinutes(2);
    private static final Duration TTL_INTRADAY_OPEN = Duration.ofMinutes(5);
    private static final TypeReference<JsonNode> JSON_NODE_TYPE = new TypeReference<>() {};
    private static final long MIN_REQUEST_INTERVAL_MS = 2_000;
    private static final long MIN_CHART_INTERVAL_MS = 300;
    private static final int MAX_CHART_RETRIES = 2;
    private static final long CHART_BACKOFF_MS = 3_000;
    private static final Duration CRUMB_TTL = Duration.ofHours(24);
    private static final Duration CRUMB_EXTEND_ON_429 = Duration.ofHours(1);
    private static final Duration CRUMB_COOLDOWN_NO_CRUMB = Duration.ofMinutes(1);
    private static final String REDIS_CRUMB_KEY = "yahoo:crumb";
    private static final TypeReference<Map<String, String>> CRUMB_MAP_TYPE = new TypeReference<>() {};
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient[] webClients;
    private final RedisCacheAdapter cache;
    private final String[] proxyUrls;
    private final String[] chartHosts;
    private final AtomicInteger hostIndex = new AtomicInteger(0);

    private final ReentrantLock crumbLock = new ReentrantLock();
    private final java.util.concurrent.ConcurrentHashMap<String, Object> summaryLocks = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile String crumb;
    private volatile String cookie;
    private volatile int activeProxyIndex;
    private volatile long crumbExpiresAt;
    private volatile long lastYahooRequestAt;
    private volatile long lastChartRequestAt;
    private volatile boolean crumbRotated;

    @Autowired
    public YahooFinanceClient(RedisCacheAdapter cache,
                              @Value("${app.yahoo.proxy-url:}") String proxyUrlCsv) {
        this(BASE_URL, cache, proxyUrlCsv);
    }

    YahooFinanceClient(String baseUrl) {
        this(baseUrl, null, null);
    }

    YahooFinanceClient(String baseUrl, RedisCacheAdapter cache) {
        this(baseUrl, cache, null);
    }

    private YahooFinanceClient(String baseUrl, RedisCacheAdapter cache, String proxyUrlCsv) {
        this.cache = cache;
        this.proxyUrls = parseProxyUrls(proxyUrlCsv);
        this.chartHosts = BASE_URL.equals(baseUrl) ? CHART_HOSTS : new String[]{baseUrl};
        if (proxyUrls.length > 0) {
            this.webClients = new WebClient[proxyUrls.length];
            for (int i = 0; i < proxyUrls.length; i++) {
                this.webClients[i] = buildWebClient(baseUrl, proxyUrls[i]);
            }
            log.info("yahoo proxy pool: {} proxies configured", proxyUrls.length);
        } else {
            this.webClients = new WebClient[]{ buildWebClient(baseUrl, null) };
        }
    }

    private static String[] parseProxyUrls(String csv) {
        if (csv == null || csv.isBlank()) return new String[0];
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    private static WebClient buildWebClient(String baseUrl, String proxyUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)))
                .responseTimeout(TIMEOUT);
        if (proxyUrl != null) {
            httpClient = applyProxy(httpClient, proxyUrl);
        }
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", DEFAULT_UA)
                .defaultHeader("Referer", "https://finance.yahoo.com/")
                .defaultHeader("Origin", "https://finance.yahoo.com")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private static HttpClient applyProxy(HttpClient httpClient, String proxyUrl) {
        try {
            URI uri = URI.create(proxyUrl);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 8080;
            boolean isSocks = "socks5".equalsIgnoreCase(uri.getScheme());
            log.info("yahoo proxy: {} ({})", host + ":" + port, isSocks ? "SOCKS5" : "HTTP");
            return httpClient.proxy(p -> {
                var spec = p.type(isSocks ? ProxyProvider.Proxy.SOCKS5 : ProxyProvider.Proxy.HTTP)
                        .host(host).port(port);
                String userInfo = uri.getUserInfo();
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    spec.username(parts[0]).password(pw -> parts[1]);
                }
            });
        } catch (Exception ex) {
            log.warn("invalid yahoo proxy URL '{}': {}", proxyUrl, ex.getMessage());
            return httpClient;
        }
    }

    private WebClient activeClient() {
        return webClients[activeProxyIndex % webClients.length];
    }

    private String activeProxy() {
        if (proxyUrls.length == 0) return null;
        return proxyUrls[activeProxyIndex % proxyUrls.length];
    }

    private synchronized void rotateProxy() {
        if (proxyUrls.length <= 1) return;
        int prev = activeProxyIndex;
        activeProxyIndex = (activeProxyIndex + 1) % proxyUrls.length;
        if (activeProxyIndex != prev) {
            invalidateCrumb();
            log.info("yahoo proxy rotated: {} → {}",
                    URI.create(proxyUrls[prev]).getHost(),
                    URI.create(proxyUrls[activeProxyIndex]).getHost());
        }
    }

    private static String randomUserAgent() {
        return USER_AGENTS[ThreadLocalRandom.current().nextInt(USER_AGENTS.length)];
    }

    private String nextChartHost() {
        return chartHosts[Math.abs(hostIndex.getAndIncrement()) % chartHosts.length];
    }

    /**
     * 현재가 스냅샷. v8/finance/chart meta 필드에서 추출.
     * regularMarketPrice 가 null 이거나 0 이면 null 반환.
     */
    public Quote quote(String ticker) {
        Duration ttl = MarketStatusResolver.resolve() == MarketStatus.OPEN
                ? TTL_CHART_OPEN : MarketStatusResolver.durationUntilNextOpen();
        JsonNode root = cache != null
                ? cache.getOrLoad("yahoo:chart:" + ticker, JSON_NODE_TYPE, ttl,
                        () -> fetchChart(ticker, "1m", "1d"))
                : fetchChart(ticker, "1m", "1d");
        try {
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
        Duration ttl = MarketStatusResolver.resolve() == MarketStatus.OPEN
                ? TTL_INTRADAY_OPEN : MarketStatusResolver.durationUntilNextOpen();
        JsonNode root = cache != null
                ? cache.getOrLoad("yahoo:intraday:" + ticker, JSON_NODE_TYPE, ttl,
                        () -> fetchChart(ticker, "5m", "1d"))
                : fetchChart(ticker, "5m", "1d");
        return parseIntradayResponse(root);
    }

    private synchronized void chartThrottle() {
        long elapsed = System.currentTimeMillis() - lastChartRequestAt;
        if (elapsed < MIN_CHART_INTERVAL_MS) {
            try { Thread.sleep(MIN_CHART_INTERVAL_MS - elapsed); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        lastChartRequestAt = System.currentTimeMillis();
    }

    private JsonNode fetchChart(String ticker, String interval, String range) {
        for (int attempt = 0; attempt <= MAX_CHART_RETRIES; attempt++) {
            chartThrottle();
            String host = nextChartHost();
            String ua = randomUserAgent();
            try {
                String url = host + "/v8/finance/chart/"
                        + URLEncoder.encode(ticker, StandardCharsets.UTF_8)
                        + "?interval=" + interval + "&range=" + range;
                return activeClient().get()
                        .uri(URI.create(url))
                        .header("User-Agent", ua)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block(TIMEOUT);
            } catch (Exception ex) {
                String msg = ex.getMessage();
                if (msg != null && msg.contains("429") && attempt < MAX_CHART_RETRIES) {
                    rotateProxy();
                    long delay = CHART_BACKOFF_MS * (attempt + 1);
                    log.warn("yahoo chart 429 for {} (attempt {}), backing off {}ms", ticker, attempt + 1, delay);
                    try { Thread.sleep(delay); } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                log.warn("yahoo finance chart {} interval={} failed: {}", ticker, interval, msg);
                return null;
            }
        }
        return null;
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

        for (int attempt = 0; attempt <= MAX_CHART_RETRIES; attempt++) {
            chartThrottle();
            String host = nextChartHost();
            String ua = randomUserAgent();
            try {
                String url = host + "/v8/finance/chart/"
                        + URLEncoder.encode(ticker, StandardCharsets.UTF_8)
                        + "?period1=" + period1 + "&period2=" + period2
                        + "&interval=1d&events=div,splits";
                JsonNode root = activeClient().get()
                        .uri(URI.create(url))
                        .header("User-Agent", ua)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block(TIMEOUT);

                return parseChartResponse(ticker, root);
            } catch (Exception ex) {
                String msg = ex.getMessage();
                if (msg != null && msg.contains("429") && attempt < MAX_CHART_RETRIES) {
                    rotateProxy();
                    long delay = CHART_BACKOFF_MS * (attempt + 1);
                    log.warn("yahoo dailyCandles 429 for {} (attempt {}), backing off {}ms", ticker, attempt + 1, delay);
                    try { Thread.sleep(delay); } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                log.warn("yahoo finance {} fetch failed: {}", ticker, msg);
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
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
        long now = System.currentTimeMillis();
        if (crumb != null && now < crumbExpiresAt) return;
        if (crumb == null && now < crumbExpiresAt) return;
        crumbLock.lock();
        try {
            now = System.currentTimeMillis();
            if (crumb != null && now < crumbExpiresAt) return;
            if (crumb == null && now < crumbExpiresAt) return;
            if (crumb == null && loadCrumbFromRedis()) return;
            refreshCrumb();
        } finally {
            crumbLock.unlock();
        }
    }

    private boolean loadCrumbFromRedis() {
        if (cache == null) return false;
        if (crumbRotated) return false;
        try {
            Map<String, String> data = cache.get(REDIS_CRUMB_KEY, CRUMB_MAP_TYPE);
            if (data == null || data.get("crumb") == null || data.get("cookie") == null) return false;
            this.crumb = data.get("crumb");
            this.cookie = data.get("cookie");
            this.crumbExpiresAt = System.currentTimeMillis() + CRUMB_TTL.toMillis();
            String idx = data.get("proxyIndex");
            if (idx != null && proxyUrls.length > 0) {
                this.activeProxyIndex = Integer.parseInt(idx) % proxyUrls.length;
                log.info("yahoo crumb restored from redis (proxy index={})", activeProxyIndex);
            } else {
                log.info("yahoo crumb restored from redis");
            }
            return true;
        } catch (Exception ex) {
            log.warn("redis crumb load failed: {}", ex.getMessage());
            return false;
        }
    }

    private void persistCrumb(String crumbVal, String cookieVal) {
        if (cache == null) return;
        try {
            Map<String, String> map = new java.util.HashMap<>();
            map.put("crumb", crumbVal);
            map.put("cookie", cookieVal);
            if (proxyUrls.length > 0) {
                map.put("proxyIndex", String.valueOf(activeProxyIndex));
            }
            cache.set(REDIS_CRUMB_KEY, map, CRUMB_TTL);
            log.info("yahoo crumb persisted to redis (proxy index={})", activeProxyIndex);
        } catch (Exception ex) {
            log.warn("redis crumb persist failed: {}", ex.getMessage());
        }
    }

    // curl 사용 — Java HttpClient는 Yahoo TLS fingerprinting에 의해 429 차단됨
    private void refreshCrumb() {
        String prevCrumb = this.crumb;
        String prevCookie = this.cookie;

        int maxAttempts = Math.max(proxyUrls.length, 1);
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                String ua = randomUserAgent();
                String proxy = activeProxy();
                List<String> cmd1 = new ArrayList<>(List.of(
                        "curl", "-s", "-L", "-D", "-", "-o", "/dev/null",
                        "-A", ua,
                        "-H", "Referer: https://finance.yahoo.com/",
                        "-H", "Origin: https://finance.yahoo.com"));
                if (proxy != null) { cmd1.add("-x"); cmd1.add(proxy); }
                cmd1.add(CRUMB_INIT_URL);
                var pb1 = new ProcessBuilder(cmd1);
                var p1 = pb1.start();
                String headers = new String(p1.getInputStream().readAllBytes());
                if (!p1.waitFor(10, TimeUnit.SECONDS)) {
                    p1.destroyForcibly();
                    log.warn("yahoo crumb: cookie request timed out (proxy {})", proxy);
                    rotateProxy();
                    continue;
                }

                StringBuilder cookieBuilder = new StringBuilder();
                for (String line : headers.split("\\r?\\n")) {
                    if (line.regionMatches(true, 0, "set-cookie:", 0, 11)) {
                        String nameVal = line.substring(11).trim().split(";")[0];
                        if (!cookieBuilder.isEmpty()) cookieBuilder.append("; ");
                        cookieBuilder.append(nameVal);
                    }
                }
                String cookies = cookieBuilder.toString();
                if (cookies.isBlank()) {
                    log.warn("yahoo crumb: no cookies from init (proxy {})", proxy);
                    rotateProxy();
                    continue;
                }

                List<String> cmd2 = new ArrayList<>(List.of(
                        "curl", "-s", "-w", "\n%{http_code}",
                        "-A", ua, "-b", cookies,
                        "-H", "Referer: https://finance.yahoo.com/",
                        "-H", "Origin: https://finance.yahoo.com"));
                if (proxy != null) { cmd2.add("-x"); cmd2.add(proxy); }
                cmd2.add(QUERY2_URL + "/v1/test/getcrumb");
                var pb2 = new ProcessBuilder(cmd2);
                var p2 = pb2.start();
                String output = new String(p2.getInputStream().readAllBytes()).trim();
                if (!p2.waitFor(10, TimeUnit.SECONDS)) {
                    p2.destroyForcibly();
                    log.warn("yahoo crumb: crumb request timed out (proxy {})", proxy);
                    rotateProxy();
                    continue;
                }

                int lastNl = output.lastIndexOf('\n');
                String status = lastNl >= 0 ? output.substring(lastNl + 1).trim() : "";
                String body = lastNl >= 0 ? output.substring(0, lastNl).trim() : output;

                if ("200".equals(status) && !body.isBlank() && !body.contains(" ")) {
                    this.crumb = body;
                    this.cookie = cookies;
                    this.crumbExpiresAt = System.currentTimeMillis() + CRUMB_TTL.toMillis();
                    this.crumbRotated = false;
                    persistCrumb(body, cookies);
                    log.info("yahoo crumb refreshed via curl (proxy {}, attempt {})", proxy, attempt + 1);
                    return;
                }

                log.warn("yahoo crumb via curl: status={} proxy={} attempt={}/{}", status, proxy, attempt + 1, maxAttempts);
                if ("429".equals(status)) {
                    rotateProxy();
                    continue;
                }
                preserveExistingCrumb(prevCrumb, prevCookie);
                return;
            } catch (Exception ex) {
                log.warn("yahoo crumb via curl error (attempt {}): {}", attempt + 1, ex.getMessage());
                rotateProxy();
            }
        }

        log.warn("yahoo crumb: all {} proxies exhausted", maxAttempts);
        if (prevCrumb != null) {
            this.crumb = prevCrumb;
            this.cookie = prevCookie;
            this.crumbExpiresAt = System.currentTimeMillis() + CRUMB_EXTEND_ON_429.toMillis();
            log.info("yahoo crumb — reusing existing crumb for {}h", CRUMB_EXTEND_ON_429.toHours());
        } else {
            this.crumbExpiresAt = System.currentTimeMillis() + CRUMB_COOLDOWN_NO_CRUMB.toMillis();
        }
    }

    private void preserveExistingCrumb(String prevCrumb, String prevCookie) {
        if (prevCrumb != null) {
            this.crumb = prevCrumb;
            this.cookie = prevCookie;
        }
    }

    private void invalidateCrumb() {
        this.crumb = null;
        this.cookie = null;
        this.crumbExpiresAt = 0;
        this.crumbRotated = true;
    }

    // ── quoteSummary (Redis 24h 캐시 + curl) ──

    private JsonNode fetchQuoteSummaryRaw(String ticker) {
        if (cache == null) return fetchQuoteSummaryFromYahoo(ticker);
        String cacheKey = "yahoo:summary:" + ticker;
        JsonNode cached = cache.get(cacheKey, JSON_NODE_TYPE);
        if (cached != null) return cached;

        Object lock = summaryLocks.computeIfAbsent(ticker, k -> new Object());
        synchronized (lock) {
            cached = cache.get(cacheKey, JSON_NODE_TYPE);
            if (cached != null) return cached;

            JsonNode result = fetchQuoteSummaryFromYahoo(ticker);
            if (result != null) {
                cache.set(cacheKey, result, SUMMARY_CACHE_TTL);
            }
            return result;
        }
    }

    private JsonNode fetchQuoteSummaryFromYahoo(String ticker) {
        for (int attempt = 0; attempt <= MAX_CHART_RETRIES; attempt++) {
            ensureCrumb();
            if (crumb == null || cookie == null) return null;

            String url = QUERY2_URL + "/v10/finance/quoteSummary/" + ticker
                    + "?modules=" + ALL_MODULES
                    + "&crumb=" + URLEncoder.encode(crumb, StandardCharsets.UTF_8);

            String json = curlFetch(url, cookie);
            if (json == null || json.isBlank()) {
                if (attempt < MAX_CHART_RETRIES) {
                    long delay = CHART_BACKOFF_MS * (attempt + 1);
                    log.warn("yahoo quoteSummary fail for {} (attempt {}), retrying in {}ms", ticker, attempt + 1, delay);
                    try { Thread.sleep(delay); } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                return null;
            }

            try {
                JsonNode root = MAPPER.readTree(json);
                JsonNode result = root.path("quoteSummary").path("result");
                if (!result.isArray() || result.isEmpty()) {
                    if ("401".equals(root.path("quoteSummary").path("error").path("code").asText())) {
                        invalidateCrumb();
                    }
                    return null;
                }
                return result.get(0);
            } catch (Exception ex) {
                log.warn("yahoo quoteSummary parse error for {}: {}", ticker, ex.getMessage());
                return null;
            }
        }
        return null;
    }

    private synchronized void throttle() {
        long elapsed = System.currentTimeMillis() - lastYahooRequestAt;
        if (elapsed < MIN_REQUEST_INTERVAL_MS) {
            try { Thread.sleep(MIN_REQUEST_INTERVAL_MS - elapsed); } catch (InterruptedException ignored) {}
        }
        lastYahooRequestAt = System.currentTimeMillis();
    }

    private String curlFetch(String url, String cookies) {
        try {
            throttle();
            List<String> cmd = new ArrayList<>(List.of(
                    "curl", "-s", "-w", "\n%{http_code}",
                    "-A", randomUserAgent(), "-b", cookies,
                    "-H", "Referer: https://finance.yahoo.com/",
                    "-H", "Origin: https://finance.yahoo.com"));
            String px = activeProxy();
            if (px != null) { cmd.add("-x"); cmd.add(px); }
            cmd.add(url);
            var pb = new ProcessBuilder(cmd);
            var p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            if (p.exitValue() != 0) return null;

            int lastNl = output.lastIndexOf('\n');
            if (lastNl < 0) return null;
            String status = output.substring(lastNl + 1).trim();
            String body = output.substring(0, lastNl);

            if ("200".equals(status)) return body;
            log.warn("curlFetch status={} for {}", status, url);
            return null;
        } catch (Exception ex) {
            log.warn("curlFetch failed for {}: {}", url, ex.getMessage());
            return null;
        }
    }

    public CompanyOverview quoteSummary(String ticker) {
        try {
            JsonNode data = fetchQuoteSummaryRaw(ticker);
            return data != null ? parseQuoteSummary(data) : null;
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

    // ── analystEstimates (애널리스트 컨센서스) ──

    public AnalystEstimates analystEstimates(String ticker) {
        try {
            JsonNode data = fetchQuoteSummaryRaw(ticker);
            return data != null ? parseAnalystData(data) : null;
        } catch (Exception ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("401")) invalidateCrumb();
            log.warn("yahoo analystEstimates {} failed: {}", ticker, msg);
            return null;
        }
    }

    private AnalystEstimates parseAnalystData(JsonNode data) {
        AnalystEstimates.Rating rating = parseRating(data);
        AnalystEstimates.PriceTarget priceTarget = parsePriceTarget(data);
        List<AnalystEstimates.EarningsQuarter> earnings = parseEarnings(data);

        if (rating == null && priceTarget == null && earnings.isEmpty()) {
            return null;
        }
        return new AnalystEstimates(rating, priceTarget, earnings);
    }

    /**
     * Yahoo {raw: ...} 또는 direct number 를 모두 처리.
     * rawNum 과 달리 0 도 유효한 값으로 취급.
     */
    private static BigDecimal numVal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        JsonNode raw = node.path("raw");
        if (!raw.isMissingNode() && !raw.isNull() && raw.isNumber()) {
            return BigDecimal.valueOf(raw.asDouble()).setScale(4, RoundingMode.HALF_UP);
        }
        if (node.isNumber()) {
            return BigDecimal.valueOf(node.asDouble()).setScale(4, RoundingMode.HALF_UP);
        }
        return null;
    }

    private AnalystEstimates.Rating parseRating(JsonNode data) {
        JsonNode fd = data.path("financialData");
        BigDecimal score = numVal(fd.path("recommendationMean"));
        if (score == null) return null;

        BigDecimal opinionsBd = numVal(fd.path("numberOfAnalystOpinions"));
        Integer totalAnalysts = opinionsBd != null ? opinionsBd.intValue() : null;

        AnalystEstimates.Rating.Distribution dist = null;
        JsonNode trend = data.path("recommendationTrend").path("trend");
        if (trend.isArray() && !trend.isEmpty()) {
            JsonNode latest = trend.get(0);
            dist = new AnalystEstimates.Rating.Distribution(
                    latest.path("strongBuy").asInt(0),
                    latest.path("buy").asInt(0),
                    latest.path("hold").asInt(0),
                    latest.path("sell").asInt(0),
                    latest.path("strongSell").asInt(0)
            );
        }

        return new AnalystEstimates.Rating(
                score,
                AnalystEstimates.ratingLabel(score),
                AnalystEstimates.ratingLabelKo(score),
                totalAnalysts,
                dist
        );
    }

    private AnalystEstimates.PriceTarget parsePriceTarget(JsonNode data) {
        JsonNode fd = data.path("financialData");
        BigDecimal mean = numVal(fd.path("targetMeanPrice"));
        if (mean == null) return null;

        BigDecimal current = numVal(fd.path("currentPrice"));
        BigDecimal high = numVal(fd.path("targetHighPrice"));
        BigDecimal low = numVal(fd.path("targetLowPrice"));
        BigDecimal median = numVal(fd.path("targetMedianPrice"));

        BigDecimal upsidePercent = null;
        if (current != null && current.signum() > 0) {
            upsidePercent = mean.subtract(current)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(current, 2, RoundingMode.HALF_UP);
        }

        return new AnalystEstimates.PriceTarget(current, high, low, mean, median, upsidePercent);
    }

    private List<AnalystEstimates.EarningsQuarter> parseEarnings(JsonNode data) {
        JsonNode history = data.path("earningsHistory").path("history");
        if (!history.isArray() || history.isEmpty()) return List.of();

        List<AnalystEstimates.EarningsQuarter> result = new ArrayList<>(4);
        for (JsonNode h : history) {
            BigDecimal actual = numVal(h.path("epsActual"));
            BigDecimal estimate = numVal(h.path("epsEstimate"));
            if (actual == null && estimate == null) continue;

            BigDecimal surprise = numVal(h.path("surprisePercent"));
            BigDecimal surprisePct = surprise != null
                    ? surprise.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : null;

            String quarter = textOrNull(h.path("quarter").path("fmt"));
            if (quarter == null) {
                JsonNode rawQ = h.path("quarter").path("raw");
                if (!rawQ.isMissingNode() && rawQ.isNumber()) {
                    long epoch = rawQ.asLong();
                    LocalDate d = Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC).toLocalDate();
                    int q = (d.getMonthValue() - 1) / 3 + 1;
                    quarter = "Q" + q + " " + d.getYear();
                }
            }

            result.add(new AnalystEstimates.EarningsQuarter(
                    quarter,
                    actual,
                    estimate,
                    surprisePct,
                    AnalystEstimates.earningsResult(surprisePct)
            ));
        }
        return result;
    }
}
