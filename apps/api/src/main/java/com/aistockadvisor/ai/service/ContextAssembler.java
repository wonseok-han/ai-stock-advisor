package com.aistockadvisor.ai.service;

import com.aistockadvisor.news.domain.NewsItem;
import com.aistockadvisor.news.service.NewsService;
import com.aistockadvisor.stock.domain.IndicatorSnapshot;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.domain.StockProfile;
import com.aistockadvisor.stock.domain.TimeFrame;
import com.aistockadvisor.stock.service.IndicatorService;
import com.aistockadvisor.stock.service.QuoteService;
import com.aistockadvisor.stock.service.StockProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

    private final StockProfileService profileService;
    private final QuoteService quoteService;
    private final IndicatorService indicatorService;
    private final NewsService newsService;

    public ContextAssembler(StockProfileService profileService,
                            QuoteService quoteService,
                            IndicatorService indicatorService,
                            NewsService newsService) {
        this.profileService = profileService;
        this.quoteService = quoteService;
        this.indicatorService = indicatorService;
        this.newsService = newsService;
    }

    public Map<String, Object> assemble(String ticker, TimeFrame timeframe) {
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<StockProfile> pF = ex.submit(() -> safely(() -> profileService.getProfile(ticker)));
            Future<Quote> qF = ex.submit(() -> safely(() -> quoteService.getQuote(ticker)));
            Future<IndicatorSnapshot> iF = ex.submit(() -> safely(() -> indicatorService.compute(ticker)));
            Future<List<NewsItem>> nF = ex.submit(() -> safely(() -> newsService.getNews(ticker, 3)));

            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("ticker", ticker);
            ctx.put("timeframe", timeframe == null ? "1D" : timeframe.code());
            ctx.put("profile", profileOf(await(pF)));
            ctx.put("quote", quoteOf(await(qF)));
            ctx.put("indicators", indicatorsOf(await(iF)));
            ctx.put("recent_news", newsOf(await(nF)));
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
        return items.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", n.titleKo() != null ? n.titleKo() : n.titleEn());
            m.put("summary", n.summaryKo());
            m.put("sentiment", n.sentiment());
            m.put("published_at", n.publishedAt());
            return m;
        }).toList();
    }

    @FunctionalInterface
    private interface Loader<T> {
        T load() throws Exception;
    }
}
