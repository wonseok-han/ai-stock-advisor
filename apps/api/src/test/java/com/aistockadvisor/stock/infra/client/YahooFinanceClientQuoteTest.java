package com.aistockadvisor.stock.infra.client;

import com.aistockadvisor.stock.domain.MarketStatus;
import com.aistockadvisor.stock.domain.Quote;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * YahooFinanceClient.quote() MockWebServer 단위 테스트 (Y1 ~ Y7).
 *
 * <p>검증 매트릭스:
 * <ul>
 *   <li>Y1: 정상 응답 + regular period 현재 포함 → Quote OPEN, 필드 전부 채움</li>
 *   <li>Y2: 정상 응답 + regular period 과거 → Quote CLOSED</li>
 *   <li>Y3: chart.result 배열 빔 → null</li>
 *   <li>Y4: regularMarketPrice=0 → null</li>
 *   <li>Y5: chartPreviousClose/previousClose 둘 다 누락 → change=0, changePct=0</li>
 *   <li>Y6: HTTP 500 → null (예외 catch)</li>
 *   <li>Y7: 요청 경로 검증 — /v8/finance/chart/{symbol}?interval=1m&range=1d</li>
 * </ul>
 */
class YahooFinanceClientQuoteTest {

    private MockWebServer server;
    private YahooFinanceClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("").toString();
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        client = new YahooFinanceClient(baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("Y1: 정상 응답 + period 현재 포함 → OPEN, 필드 전부 채움")
    void y1_validOpenResponseReturnsFullQuote() {
        long now = Instant.now().getEpochSecond();
        server.enqueue(jsonResponse(buildMetaBody(
                150.25, 148.50, 151.00, 149.00, 149.50, 12345678L, now,
                now - 3600, now + 3600)));

        Quote q = client.quote("AAPL");

        assertThat(q).isNotNull();
        assertThat(q.ticker()).isEqualTo("AAPL");
        assertThat(q.price()).isEqualByComparingTo(new BigDecimal("150.25"));
        assertThat(q.previousClose()).isEqualByComparingTo(new BigDecimal("148.50"));
        assertThat(q.change()).isEqualByComparingTo(new BigDecimal("1.75"));
        // (150.25 - 148.50) / 148.50 * 100 ≈ 1.178451
        assertThat(q.changePercent().doubleValue()).isCloseTo(1.178, within(0.01));
        assertThat(q.high()).isEqualByComparingTo(new BigDecimal("151.00"));
        assertThat(q.low()).isEqualByComparingTo(new BigDecimal("149.00"));
        assertThat(q.open()).isEqualByComparingTo(new BigDecimal("149.50"));
        assertThat(q.volume()).isEqualTo(12345678L);
        assertThat(q.marketStatus()).isEqualTo(MarketStatus.OPEN);
        assertThat(q.priceLabel()).isEqualTo("실시간 (약 1~2분 지연)");
    }

    @Test
    @DisplayName("Y2: period 과거 → CLOSED, priceLabel 에 날짜 포함")
    void y2_closedPeriodReturnsClosedStatus() {
        long past = Instant.now().getEpochSecond() - 86400;
        server.enqueue(jsonResponse(buildMetaBody(
                150.0, 148.0, 151.0, 149.0, 149.0, 1000L, past,
                past - 3600, past - 1800)));

        Quote q = client.quote("AAPL");

        assertThat(q).isNotNull();
        assertThat(q.marketStatus()).isEqualTo(MarketStatus.CLOSED);
        assertThat(q.priceLabel()).contains("정규장 종가");
    }

    @Test
    @DisplayName("Y3: chart.result 빈 배열 → null")
    void y3_emptyResultReturnsNull() {
        server.enqueue(jsonResponse("{\"chart\":{\"result\":[],\"error\":null}}"));

        Quote q = client.quote("UNKNOWN");

        assertThat(q).isNull();
    }

    @Test
    @DisplayName("Y4: regularMarketPrice=0 → null")
    void y4_zeroPriceReturnsNull() {
        long now = Instant.now().getEpochSecond();
        server.enqueue(jsonResponse(buildMetaBody(
                0.0, 148.0, 151.0, 149.0, 149.0, 1000L, now,
                now - 3600, now + 3600)));

        Quote q = client.quote("AAPL");

        assertThat(q).isNull();
    }

    @Test
    @DisplayName("Y5: prevClose 누락 → change=0, changePct=0, Quote 반환")
    void y5_missingPrevCloseYieldsZeroChange() {
        long now = Instant.now().getEpochSecond();
        // chartPreviousClose / previousClose 모두 null
        String body = "{\"chart\":{\"result\":[{\"meta\":{"
                + "\"regularMarketPrice\":150.25,"
                + "\"regularMarketTime\":" + now + ","
                + "\"currentTradingPeriod\":{\"regular\":{\"start\":" + (now - 3600) + ",\"end\":" + (now + 3600) + "}}"
                + "}}],\"error\":null}}";
        server.enqueue(jsonResponse(body));

        Quote q = client.quote("AAPL");

        assertThat(q).isNotNull();
        assertThat(q.price()).isEqualByComparingTo(new BigDecimal("150.25"));
        assertThat(q.change()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(q.changePercent()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Y6: HTTP 500 → null (예외 catch, fallback 친화)")
    void y6_serverErrorReturnsNull() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("upstream"));

        Quote q = client.quote("AAPL");

        assertThat(q).isNull();
    }

    @Test
    @DisplayName("Y7: 요청 경로는 /v8/finance/chart/{symbol}?interval=1m&range=1d")
    void y7_requestPathMatchesYahooContract() throws InterruptedException {
        long now = Instant.now().getEpochSecond();
        server.enqueue(jsonResponse(buildMetaBody(
                150.0, 148.0, 151.0, 149.0, 149.0, 1000L, now,
                now - 3600, now + 3600)));

        client.quote("AAPL");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).startsWith("/v8/finance/chart/AAPL");
        assertThat(req.getPath()).contains("interval=1m");
        assertThat(req.getPath()).contains("range=1d");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static String buildMetaBody(
            double price, double prevClose, double high, double low,
            double open, long volume, long marketTime,
            long regularStart, long regularEnd) {
        return "{\"chart\":{\"result\":[{\"meta\":{"
                + "\"regularMarketPrice\":" + price + ","
                + "\"chartPreviousClose\":" + prevClose + ","
                + "\"regularMarketDayHigh\":" + high + ","
                + "\"regularMarketDayLow\":" + low + ","
                + "\"regularMarketOpen\":" + open + ","
                + "\"regularMarketVolume\":" + volume + ","
                + "\"regularMarketTime\":" + marketTime + ","
                + "\"currentTradingPeriod\":{\"regular\":{"
                + "\"start\":" + regularStart + ",\"end\":" + regularEnd + "}}"
                + "}}],\"error\":null}}";
    }

    private static org.assertj.core.data.Offset<Double> within(double offset) {
        return org.assertj.core.data.Offset.offset(offset);
    }
}
