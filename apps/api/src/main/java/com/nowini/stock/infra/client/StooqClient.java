package com.nowini.stock.infra.client;

import com.nowini.cache.RedisCacheAdapter;
import com.nowini.stock.domain.MarketStatus;
import com.nowini.stock.domain.MarketStatusResolver;
import com.nowini.stock.domain.Quote;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

/**
 * Stooq 무료 시세 클라이언트 (API key 불필요, CSV).
 * <p>
 * Yahoo Finance가 datacenter 프록시 차단으로 막히는 상황에서, 지수/선물/상품/환율
 * 시세를 보강하는 fallback 소스. 지수(^spx/^ndq/^dji), 선물(es.f/nq.f),
 * 상품(xauusd/xagusd/cl.f/hg.f), 환율(usdkrw)을 커버한다.
 * Russell2000/VIX/DXY/10Y Treasury 등은 미지원(N/D)이라 호출하지 않는다.
 * <p>
 * 엔드포인트: {@code https://stooq.com/q/l/?s={symbol}&f=sd2t2ohlcvp&e=csv}
 * 응답(헤더 없음): {@code symbol,date,time,open,high,low,close,volume,prev}
 */
@Component
public class StooqClient {

    private static final Logger log = LoggerFactory.getLogger(StooqClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Duration TTL_OPEN = Duration.ofMinutes(2);
    private static final TypeReference<Quote> QUOTE_TYPE = new TypeReference<>() {};

    private final WebClient webClient;
    private final RedisCacheAdapter cache;

    public StooqClient(RedisCacheAdapter cache) {
        this.cache = cache;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)))
                .responseTimeout(TIMEOUT);
        this.webClient = WebClient.builder()
                .baseUrl("https://stooq.com")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /** Stooq 심볼로 시세 조회. 심볼이 비어 있거나(미지원) 데이터가 없으면 null. */
    public Quote quote(String stooqSymbol) {
        if (stooqSymbol == null || stooqSymbol.isBlank()) return null;
        if (cache == null) return fetch(stooqSymbol);
        Duration ttl = MarketStatusResolver.resolve() == MarketStatus.OPEN
                ? TTL_OPEN : MarketStatusResolver.durationUntilNextOpen();
        return cache.getOrLoad("stooq:quote:" + stooqSymbol, QUOTE_TYPE, ttl, () -> fetch(stooqSymbol));
    }

    private Quote fetch(String stooqSymbol) {
        try {
            String csv = webClient.get()
                    .uri(b -> b.path("/q/l/")
                            .queryParam("s", stooqSymbol)
                            .queryParam("f", "sd2t2ohlcvp")
                            .queryParam("e", "csv")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            return parse(stooqSymbol, csv);
        } catch (Exception ex) {
            log.warn("stooq quote {} failed: {}", stooqSymbol, ex.getMessage());
            return null;
        }
    }

    /** CSV 한 줄 파싱: symbol,date,time,open,high,low,close,volume,prev */
    private Quote parse(String stooqSymbol, String csv) {
        if (csv == null || csv.isBlank()) return null;
        String[] p = csv.strip().split(",", -1);
        if (p.length < 9) return null;

        BigDecimal close = parseDecimal(p[6]);
        if (close == null) return null;  // N/D → 미지원 심볼

        BigDecimal open = parseDecimal(p[3]);
        BigDecimal high = parseDecimal(p[4]);
        BigDecimal low = parseDecimal(p[5]);
        BigDecimal prev = parseDecimal(p[8]);
        long volume = parseLong(p[7]);

        BigDecimal change = null;
        BigDecimal changePercent = null;
        if (prev != null && prev.signum() > 0) {
            change = close.subtract(prev);
            changePercent = change.divide(prev, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        MarketStatus status = MarketStatusResolver.resolve();
        return new Quote(stooqSymbol, close, change, changePercent,
                high, low, open, prev, volume, now, status, null, null, null);
    }

    private static BigDecimal parseDecimal(String s) {
        if (s == null) return null;
        s = s.strip();
        if (s.isEmpty() || "N/D".equalsIgnoreCase(s)) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static long parseLong(String s) {
        if (s == null) return 0;
        s = s.strip();
        if (s.isEmpty() || "N/D".equalsIgnoreCase(s)) return 0;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
