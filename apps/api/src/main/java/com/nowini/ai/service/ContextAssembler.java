package com.nowini.ai.service;

import com.nowini.ai.domain.TimingVerdict;
import com.nowini.market.domain.MarketIndex;
import com.nowini.market.domain.MarketOverviewResponse;
import com.nowini.market.service.MarketOverviewService;
import com.nowini.news.domain.NewsItem;
import com.nowini.news.service.NewsService;
import com.nowini.sec.domain.SecFiling;
import com.nowini.sec.domain.SecFinancials;
import com.nowini.sec.service.SecFilingService;
import com.nowini.stock.domain.AnalystEstimates;
import com.nowini.stock.domain.CompanyOverview;
import com.nowini.stock.domain.IndicatorSnapshot;
import com.nowini.stock.domain.Quote;
import com.nowini.stock.domain.StockProfile;
import com.nowini.stock.service.AnalystEstimatesService;
import com.nowini.stock.service.CompanyOverviewService;
import com.nowini.stock.service.IndicatorService;
import com.nowini.stock.service.QuoteService;
import com.nowini.stock.service.StockProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
    private final SecFilingService secFilingService;
    private final MarketOverviewService marketOverviewService;
    private final CompanyOverviewService companyOverviewService;
    private final TimingScorer timingScorer;

    public ContextAssembler(StockProfileService profileService,
                            QuoteService quoteService,
                            IndicatorService indicatorService,
                            NewsService newsService,
                            AnalystEstimatesService analystService,
                            SecFilingService secFilingService,
                            MarketOverviewService marketOverviewService,
                            CompanyOverviewService companyOverviewService,
                            TimingScorer timingScorer) {
        this.profileService = profileService;
        this.quoteService = quoteService;
        this.indicatorService = indicatorService;
        this.newsService = newsService;
        this.analystService = analystService;
        this.secFilingService = secFilingService;
        this.marketOverviewService = marketOverviewService;
        this.companyOverviewService = companyOverviewService;
        this.timingScorer = timingScorer;
    }

    /** LLM 프롬프트용 컨텍스트 맵 + 코드로 계산한 진입 타이밍(결정론적). */
    public record AssembledContext(Map<String, Object> prompt, TimingVerdict timing) {}

    public AssembledContext assemble(String ticker) {
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<StockProfile> pF = ex.submit(() -> safely(() -> profileService.getProfile(ticker)));
            Future<Quote> qF = ex.submit(() -> safely(() -> quoteService.getQuote(ticker)));
            Future<IndicatorSnapshot> iF = ex.submit(() -> safely(() -> indicatorService.compute(ticker)));
            Future<List<NewsItem>> nF = ex.submit(() -> safely(() -> newsService.getNews(ticker, NEWS_LIMIT)));
            Future<AnalystEstimates> aF = ex.submit(() -> safely(() -> analystService.getEstimates(ticker)));
            Future<List<SecFiling>> sfF = ex.submit(() -> safely(() -> secFilingService.getRecentFilings(ticker, 5)));
            Future<SecFinancials> sxF = ex.submit(() -> safely(() -> secFilingService.getFinancials(ticker)));
            Future<BigDecimal> vixF = ex.submit(() -> safely(this::fetchVix));
            Future<CompanyOverview> coF = ex.submit(() -> safely(() -> companyOverviewService.getOverview(ticker)));

            Quote quote = await(qF);
            IndicatorSnapshot ind = await(iF);
            AnalystEstimates analyst = await(aF);
            BigDecimal vix = await(vixF);

            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("ticker", ticker);
            ctx.put("profile", profileOf(await(pF)));
            ctx.put("quote", quoteOf(quote));
            ctx.put("indicators", indicatorsOf(ind));
            ctx.put("recent_news", newsOf(await(nF)));
            ctx.put("analyst_estimates", analystOf(analyst));
            ctx.put("sec_filings", secFilingsOf(await(sfF)));
            ctx.put("sec_financials", secFinancialsOf(await(sxF)));
            ctx.put("fundamentals", fundamentalsOf(await(coF)));
            if (vix != null) ctx.put("vix", vix);

            // 타이밍은 LLM이 아니라 코드가 결정론적으로 계산
            TimingVerdict timing = timingScorer.score(quote, ind, analyst, vix);
            return new AssembledContext(ctx, timing);
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

    private Map<String, Object> fundamentalsOf(CompanyOverview co) {
        if (co == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        if (co.peRatio() != null) m.put("pe_ratio", co.peRatio());
        if (co.eps() != null) m.put("eps", co.eps());
        if (co.sector() != null) m.put("sector", co.sector());
        if (co.beta() != null) m.put("beta", co.beta());
        return m.isEmpty() ? null : m;
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
        m.put("volume", q.volume());
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
        if (ind.avgVolume20d() > 0) {
            m.put("avg_volume_20d", ind.avgVolume20d());
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

    private List<Map<String, Object>> secFilingsOf(List<SecFiling> filings) {
        if (filings == null || filings.isEmpty()) return null;
        return filings.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("form", f.form());
            m.put("event_category", f.eventCategory());
            m.put("filed_at", f.filedAt() != null ? f.filedAt().toString() : null);
            m.put("days_ago", f.daysAgo());
            if (f.summaryKo() != null) {
                m.put("summary", f.summaryKo());
            }
            return m;
        }).toList();
    }

    private Map<String, Object> secFinancialsOf(SecFinancials sf) {
        if (sf == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        if (sf.revenueHistory() != null && !sf.revenueHistory().isEmpty()) {
            m.put("revenue_trend", sf.revenueHistory().stream()
                    .map(SecFinancials.QuarterValue::value).toList());
            m.put("quarters", sf.revenueHistory().stream()
                    .map(SecFinancials.QuarterValue::quarter).toList());
        }
        if (sf.netIncomeHistory() != null && !sf.netIncomeHistory().isEmpty()) {
            m.put("net_income_trend", sf.netIncomeHistory().stream()
                    .map(SecFinancials.QuarterValue::value).toList());
        }
        m.put("unit", sf.unit());
        return m.size() <= 1 ? null : m;
    }

    private BigDecimal fetchVix() {
        MarketOverviewResponse overview = marketOverviewService.getOverview();
        if (overview == null || overview.macro() == null) return null;
        return overview.macro().stream()
                .filter(m -> "VIX".equals(m.name()))
                .map(MarketIndex::price)
                .findFirst()
                .orElse(null);
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
