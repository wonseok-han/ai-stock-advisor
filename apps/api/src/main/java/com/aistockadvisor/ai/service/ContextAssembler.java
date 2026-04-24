package com.aistockadvisor.ai.service;

import com.aistockadvisor.news.domain.NewsItem;
import com.aistockadvisor.news.service.NewsService;
import com.aistockadvisor.stock.domain.AnalystEstimates;
import com.aistockadvisor.stock.domain.IndicatorSnapshot;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.domain.StockProfile;
import com.aistockadvisor.stock.service.AnalystEstimatesService;
import com.aistockadvisor.stock.service.IndicatorService;
import com.aistockadvisor.stock.service.QuoteService;
import com.aistockadvisor.stock.service.StockProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * AI 시그널 컨텍스트 조립.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §5.1
 *
 * <p>Quote / IndicatorSnapshot / 최근 뉴스 요약을 구조화 Map 으로 합침 — 프롬프트에 JSON 으로 주입.
 * 외부 호출은 virtual-thread 병렬, 개별 블록 실패는 null 로 처리해 부분 컨텍스트 허용.
 */
@Component
public class ContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(ContextAssembler.class);

    /**
     * 프롬프트에 주입할 최근 뉴스 개수.
     * 참조: docs/02-design/features/ai-analysis-deepening.design.md §3.3 — 3 → 5 로 확대.
     */
    private static final int NEWS_LIMIT = 5;
    private static final long FRESH_HOURS = 24L;
    private static final long RECENT_HOURS = 72L;

    private final StockProfileService profileService;
    private final QuoteService quoteService;
    private final IndicatorService indicatorService;
    private final NewsService newsService;
    private final AnalystEstimatesService analystService;

    public ContextAssembler(StockProfileService profileService,
                            QuoteService quoteService,
                            IndicatorService indicatorService,
                            NewsService newsService,
                            AnalystEstimatesService analystService) {
        this.profileService = profileService;
        this.quoteService = quoteService;
        this.indicatorService = indicatorService;
        this.newsService = newsService;
        this.analystService = analystService;
    }

    public Map<String, Object> assemble(String ticker) {
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<StockProfile> pF = ex.submit(() -> safely(() -> profileService.getProfile(ticker)));
            Future<Quote> qF = ex.submit(() -> safely(() -> quoteService.getQuote(ticker)));
            Future<IndicatorSnapshot> iF = ex.submit(() -> safely(() -> indicatorService.compute(ticker)));
            Future<List<NewsItem>> nF = ex.submit(() -> safely(() -> newsService.getNews(ticker, NEWS_LIMIT)));
            Future<AnalystEstimates> aF = ex.submit(() -> safely(() -> analystService.getEstimates(ticker)));

            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("ticker", ticker);
            ctx.put("profile", profileOf(await(pF)));
            ctx.put("quote", quoteOf(await(qF)));
            ctx.put("indicators", indicatorsOf(await(iF)));
            ctx.put("recent_news", newsOf(await(nF)));
            ctx.put("analyst_estimates", analystOf(await(aF)));
            return ctx;
        }
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T safely(Loader<T> loader) {
        try {
            return loader.load();
        } catch (Exception ex) {
            log.debug("context loader failed: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> profileOf(StockProfile p) {
        if (p == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.name());
        m.put("exchange", p.exchange());
        m.put("industry", p.industry());
        m.put("market_cap", p.marketCap());
        return m;
    }

    private Map<String, Object> quoteOf(Quote q) {
        if (q == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("price", q.price());
        m.put("change", q.change());
        m.put("change_pct", q.changePercent());
        m.put("high", q.high());
        m.put("low", q.low());
        m.put("open", q.open());
        m.put("previous_close", q.previousClose());
        m.put("fifty_two_week_high", q.week52High());
        m.put("fifty_two_week_low", q.week52Low());
        return m;
    }

    private Map<String, Object> indicatorsOf(IndicatorSnapshot ind) {
        if (ind == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        if (ind.macd() != null) {
            m.put("macd", Map.of(
                    "macd", ind.macd().macd(),
                    "signal", ind.macd().signal(),
                    "histogram", ind.macd().histogram()
            ));
        }
        if (ind.bollinger() != null) {
            m.put("bollinger", Map.of(
                    "upper", ind.bollinger().upper(),
                    "middle", ind.bollinger().middle(),
                    "lower", ind.bollinger().lower(),
                    "percent_b", ind.bollinger().percentB()
            ));
        }
        m.put("rsi", ind.rsi14());
        if (ind.movingAverage() != null) {
            m.put("ma", Map.of(
                    "ma5", ind.movingAverage().ma5(),
                    "ma20", ind.movingAverage().ma20(),
                    "ma60", ind.movingAverage().ma60()
            ));
        }
        return m;
    }

    private List<Map<String, Object>> newsOf(List<NewsItem> items) {
        if (items == null || items.isEmpty()) return List.of();
        Instant now = Instant.now();
        return items.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", n.titleKo() != null ? n.titleKo() : n.titleEn());
            m.put("summary", n.summaryKo());
            m.put("sentiment", n.sentiment());
            m.put("published_at", n.publishedAt());
            if (n.publishedAt() != null) {
                long hoursAgo = Duration.between(n.publishedAt(), now).toHours();
                if (hoursAgo < 0) hoursAgo = 0;
                m.put("hours_ago", hoursAgo);
                m.put("freshness", freshnessOf(hoursAgo));
            }
            return m;
        }).toList();
    }

    private Map<String, Object> analystOf(AnalystEstimates a) {
        if (a == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        if (a.rating() != null) {
            m.put("consensus_score", a.rating().score());
            m.put("consensus_label", a.rating().labelKo());
            m.put("total_analysts", a.rating().totalAnalysts());
        }
        if (a.priceTarget() != null) {
            m.put("target_mean", a.priceTarget().mean());
            m.put("target_high", a.priceTarget().high());
            m.put("target_low", a.priceTarget().low());
            m.put("upside_percent", a.priceTarget().upsidePercent());
        }
        if (a.earnings() != null && !a.earnings().isEmpty()) {
            m.put("recent_earnings", a.earnings().stream().map(eq -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("quarter", eq.quarter());
                e.put("eps_actual", eq.epsActual());
                e.put("eps_estimate", eq.epsEstimate());
                e.put("result", eq.result());
                return e;
            }).toList());
        }
        return m.isEmpty() ? null : m;
    }

    private static String freshnessOf(long hoursAgo) {
        if (hoursAgo < FRESH_HOURS) return "FRESH";
        if (hoursAgo < RECENT_HOURS) return "RECENT";
        return "STALE";
    }

    @FunctionalInterface
    private interface Loader<T> {
        T load() throws Exception;
    }
}
