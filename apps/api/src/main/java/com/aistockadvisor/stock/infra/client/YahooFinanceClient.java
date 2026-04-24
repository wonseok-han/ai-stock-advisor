package com.aistockadvisor.stock.infra.client;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.stock.domain.AnalystEstimates;
import com.aistockadvisor.stock.domain.Candle;
import com.aistockadvisor.stock.domain.CompanyOverview;
import com.aistockadvisor.stock.domain.MarketStatus;
import com.aistockadvisor.stock.domain.MarketStatusResolver;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.infra.CandleEntity;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";
    private static final String ALL_MODULES = "summaryDetail,defaultKeyStatistics,assetProfile,financialData,recommendationTrend,earningsHistory";
    private static final Duration SUMMARY_CACHE_TTL = Duration.ofHours(24);
    private static final Duration TTL_CHART_OPEN = Duration.ofSeconds(30);
    private static final Duration TTL_INTRADAY_OPEN = Duration.ofMinutes(5);
    private static final TypeReference<JsonNode> JSON_NODE_TYPE = new TypeReference<>() {};
    private static final long MIN_REQUEST_INTERVAL_MS = 2_000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final RedisCacheAdapter cache;

    private final ReentrantLock crumbLock = new ReentrantLock();
    private volatile String crumb;
    private volatile String cookie;
    private volatile long crumbExpiresAt;
    private volatile long lastYahooRequestAt;

    @Autowired
    public YahooFinanceClient(RedisCacheAdapter cache) {
        this(BASE_URL, cache);
    }

    YahooFinanceClient(String baseUrl) {
        this(baseUrl, null);
    }

    YahooFinanceClient(String baseUrl, RedisCacheAdapter cache) {
        this.cache = cache;
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

    private JsonNode fetchChart(String ticker, String interval, String range) {
        try {
            return webClient.get()
                    .uri(b -> b.path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", interval)
                            .queryParam("range", range)
                            .build(ticker))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(TIMEOUT);
        } catch (Exception ex) {
            log.warn("yahoo finance chart {} interval={} failed: {}", ticker, interval, ex.getMessage());
            return null;
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
        long now = System.currentTimeMillis();
        if (crumb != null && now < crumbExpiresAt) return;
        // 429 쿨다운 중에는 crumb 갱신 시도하지 않음
        if (crumb == null && now < crumbExpiresAt) return;
        crumbLock.lock();
        try {
            now = System.currentTimeMillis();
            if (crumb != null && now < crumbExpiresAt) return;
            if (crumb == null && now < crumbExpiresAt) return;
            refreshCrumb();
        } finally {
            crumbLock.unlock();
        }
    }

    // curl 사용 — Java HttpClient는 Yahoo TLS fingerprinting에 의해 429 차단됨
    private void refreshCrumb() {
        try {
            var pb1 = new ProcessBuilder(
                    "curl", "-s", "-L", "-D", "-", "-o", "/dev/null",
                    "-A", USER_AGENT, CRUMB_INIT_URL
            );
            var p1 = pb1.start();
            String headers = new String(p1.getInputStream().readAllBytes());
            if (!p1.waitFor(10, TimeUnit.SECONDS)) {
                p1.destroyForcibly();
                log.warn("yahoo crumb: cookie request timed out");
                return;
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
                log.warn("yahoo crumb: no cookies from init");
                this.crumb = null;
                return;
            }

            var pb2 = new ProcessBuilder(
                    "curl", "-s", "-w", "\n%{http_code}",
                    "-A", USER_AGENT,
                    "-b", cookies,
                    QUERY2_URL + "/v1/test/getcrumb"
            );
            var p2 = pb2.start();
            String output = new String(p2.getInputStream().readAllBytes()).trim();
            if (!p2.waitFor(10, TimeUnit.SECONDS)) {
                p2.destroyForcibly();
                log.warn("yahoo crumb: crumb request timed out");
                return;
            }

            int lastNl = output.lastIndexOf('\n');
            String status = lastNl >= 0 ? output.substring(lastNl + 1).trim() : "";
            String body = lastNl >= 0 ? output.substring(0, lastNl).trim() : output;

            if ("200".equals(status) && !body.isBlank() && !body.contains(" ")) {
                this.crumb = body;
                this.cookie = cookies;
                this.crumbExpiresAt = System.currentTimeMillis() + Duration.ofHours(4).toMillis();
                log.info("yahoo crumb refreshed via curl");
            } else {
                log.warn("yahoo crumb via curl: status={} body={}", status, body);
                this.crumb = null;
                if ("429".equals(status)) {
                    this.crumbExpiresAt = System.currentTimeMillis() + Duration.ofMinutes(5).toMillis();
                }
            }
        } catch (Exception ex) {
            log.warn("yahoo crumb via curl error: {}", ex.getMessage());
            this.crumb = null;
        }
    }

    private void invalidateCrumb() {
        this.crumb = null;
        this.crumbExpiresAt = 0;
    }

    // ── quoteSummary (Redis 24h 캐시 + curl) ──

    private JsonNode fetchQuoteSummaryRaw(String ticker) {
        if (cache == null) return fetchQuoteSummaryFromYahoo(ticker);
        return cache.getOrLoad("yahoo:summary:" + ticker, JSON_NODE_TYPE, SUMMARY_CACHE_TTL,
                () -> fetchQuoteSummaryFromYahoo(ticker));
    }

    private JsonNode fetchQuoteSummaryFromYahoo(String ticker) {
        ensureCrumb();
        if (crumb == null || cookie == null) return null;

        String url = QUERY2_URL + "/v10/finance/quoteSummary/" + ticker
                + "?modules=" + ALL_MODULES
                + "&crumb=" + URLEncoder.encode(crumb, StandardCharsets.UTF_8);

        String json = curlFetch(url, cookie);
        if (json == null || json.isBlank()) return null;

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
            var pb = new ProcessBuilder(
                    "curl", "-s", "-w", "\n%{http_code}",
                    "-A", USER_AGENT, "-b", cookies, url);
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
            if ("429".equals(status)) {
                crumbExpiresAt = System.currentTimeMillis() + Duration.ofMinutes(5).toMillis();
            }
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
