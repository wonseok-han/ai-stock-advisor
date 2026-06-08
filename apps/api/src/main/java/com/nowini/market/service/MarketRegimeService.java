package com.nowini.market.service;

import com.nowini.ai.infra.LlmClient;
import com.nowini.cache.RedisCacheAdapter;
import com.nowini.common.metrics.LlmMetrics;
import com.nowini.common.prompt.PromptLoader;
import com.nowini.market.domain.MarketRegimeAiResponse;
import com.nowini.market.domain.MarketRegimeResponse;
import com.nowini.market.domain.MarketRegimeResponse.Axes;
import com.nowini.market.domain.MarketRegimeResponse.Axis;
import com.nowini.market.domain.MarketRegimeResponse.Composite;
import com.nowini.market.domain.MarketRegimeResponse.Indicator;
import com.nowini.market.domain.SectorMomentum;
import com.nowini.market.infra.CnnFearGreedClient;
import com.nowini.market.infra.CnnFearGreedClient.FearGreedSnapshot;
import com.nowini.market.infra.FmpClient;
import com.nowini.market.infra.FredClient;
import com.nowini.market.infra.FredClient.FredObservation;
import com.nowini.stock.domain.MarketStatusResolver;
import com.nowini.stock.domain.Quote;
import com.nowini.stock.infra.client.YahooFinanceClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 시장 국면 종합 — FRED + CNN + 실시간 시세 소스 9개 지표를 4축으로 수집·정규화.
 * VIX는 대시보드와 값을 맞추기 위해 실시간 시세(FMP→Yahoo) 우선, 실패 시 FRED VIXCLS(전일 종가) 폴백.
 * <p>
 * 밸류에이션(버핏지수) / 위험심리(Fear&Greed, 신용스프레드, VIX) /
 * 매크로(금리차 10Y-2Y·10Y-3M, 실업률, 순유동성) / 추세폭(S&P 200일선).
 * composite(0=공포/저평가 ~ 100=과열/고평가)는 과열도가 명확한 지표(버핏·Fear&Greed·신용·200일선)만 정규화 평균.
 * 매크로 사이클 지표(금리차·실업률·순유동성)와 VIX는 zone 표시만(composite 미반영).
 * 부분 실패 허용. AI 해석은 로그인 전용(/regime/ai), 프롬프트는 classpath:prompts/market-regime.system.txt.
 * <p>
 * 참조: docs/02-design/features/market-regime.design.md
 */
@Service
public class MarketRegimeService {

    private static final Logger log = LoggerFactory.getLogger(MarketRegimeService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<MarketRegimeResponse> TYPE = new TypeReference<>() {};
    private static final TypeReference<MarketRegimeAiResponse> AI_TYPE = new TypeReference<>() {};
    private static final String AI_PROMPT_FILE = "market-regime.system.txt";
    private static final String DISCLAIMER =
            "본 지표는 투자 자문이 아닌 정보 제공·참고용입니다. 투자 판단과 책임은 사용자 본인에게 있습니다.";

    private final FredClient fred;
    private final CnnFearGreedClient cnn;
    private final FmpClient fmpClient;
    private final YahooFinanceClient yahooFinanceClient;
    private final LlmClient llm;
    private final PromptLoader promptLoader;
    private final RedisCacheAdapter cache;
    private final SectorPerformanceService sectorService;

    public MarketRegimeService(FredClient fred, CnnFearGreedClient cnn,
                               FmpClient fmpClient, YahooFinanceClient yahooFinanceClient,
                               LlmClient llm, PromptLoader promptLoader, RedisCacheAdapter cache,
                               SectorPerformanceService sectorService) {
        this.fred = fred;
        this.cnn = cnn;
        this.fmpClient = fmpClient;
        this.yahooFinanceClient = yahooFinanceClient;
        this.llm = llm;
        this.promptLoader = promptLoader;
        this.cache = cache;
        this.sectorService = sectorService;
    }

    /** 장마감 기준 일 단위 갱신 — 다음 정규장 마감까지 캐시 유지(주말은 월요일 마감까지). */
    private static Duration ttl() {
        return MarketStatusResolver.durationUntilNextClose();
    }

    public MarketRegimeResponse getRegime() {
        // 4축이 모두 채워진 완전한 응답만 풀 TTL 캐시. 일부 지표(예: 버핏지수)가 FRED 일시
        // 실패로 빠지면 짧은 TTL로만 캐시해 곧 자동 재시도(자가복구) — 하루 종일 고착 방지.
        return cache.getOrLoad("market:regime", TYPE, ttl(), this::fetch,
                MarketRegimeService::isComplete);
    }

    /** 4축(밸류에이션·위험심리·매크로·추세) 모두 지표가 있고 종합점수도 있으면 완전한 응답. */
    private static boolean isComplete(MarketRegimeResponse r) {
        if (r.composite() == null || r.axes() == null) return false;
        return notEmpty(r.axes().valuation()) && notEmpty(r.axes().riskSentiment())
                && notEmpty(r.axes().macro()) && notEmpty(r.axes().trendBreadth());
    }

    private static boolean notEmpty(Axis axis) {
        return axis != null && axis.indicators() != null && !axis.indicators().isEmpty();
    }

    /** 국면 지표 캐시 워밍 (콜드패스 방지). AI 해석은 비싼 LLM이라 워밍 제외. */
    public void refresh() {
        MarketRegimeResponse data = fetch();
        // 불완전(일부 축 비어있음) 결과로 last-good 캐시를 덮어쓰지 않음
        if (isComplete(data)) {
            cache.set("market:regime", data, ttl());
        }
    }

    /** AI 해석 (로그인 사용자 전용). 동일 지표 스냅샷 기반, 실패 시 aiSummary=null. */
    public MarketRegimeAiResponse getRegimeAi() {
        return cache.getOrLoad("market:regime:ai", AI_TYPE, ttl(), () -> {
            MarketRegimeResponse regime = getRegime();
            return new MarketRegimeAiResponse(regime.asOf(), generateAiSummary(regime), DISCLAIMER);
        });
    }

    private MarketRegimeResponse fetch() {
        // ── 밸류에이션: 버핏지수 = (시총 NCBEILQ027S[백만$]/1000 → 십억$) / GDP[십억$] × 100 ──
        FredObservation ncb = fred.latestValue("NCBEILQ027S");
        FredObservation gdp = fred.latestValue("GDP");
        Double buffett = (ncb != null && gdp != null && gdp.value() > 0)
                ? (ncb.value() / 1000.0) / gdp.value() * 100 : null;

        // ── 위험·심리 ──
        FearGreedSnapshot fg = cnn.fetch();
        FredObservation credit = fred.latestValue("BAMLH0A0HYM2");
        // VIX: 대시보드와 값을 맞추기 위해 실시간 시세 우선. 실패 시 FRED VIXCLS(전일 종가)로 폴백.
        Double vix = liveVix();
        if (vix == null) {
            FredObservation vixClose = fred.latestValue("VIXCLS");
            if (vixClose != null) vix = vixClose.value();
        }

        // ── 매크로 ──
        FredObservation yc2 = fred.latestValue("T10Y2Y");
        FredObservation yc3 = fred.latestValue("T10Y3M");
        FredObservation unemp = fred.latestValue("UNRATE");
        Double netLiq = netLiquidity();   // 조$

        // ── 추세·폭: S&P500 200일선 이격도(%) ──
        Double sp200dev = sp500DeviationFrom200dma();

        // 지표 조립 (position: 스케일 내 위치 0~100, 반원 게이지 바늘용)
        List<Indicator> valuation = new ArrayList<>();
        if (buffett != null) {
            valuation.add(Indicator.of("buffett", "버핏지수", round(buffett), "%",
                    buffettZone(buffett), buffettNote(buffett), clamp(buffett / 250 * 100)));
        }

        List<Indicator> risk = new ArrayList<>();
        if (fg != null) {
            risk.add(new Indicator("fearGreed", "공포·탐욕 지수", round(fg.score()), null,
                    fearGreedZone(fg.score()), null,
                    fg.prev1Week(), fg.prev1Month(), fg.prev1Year(), clamp(fg.score())));
        }
        if (credit != null) {
            risk.add(Indicator.of("creditSpread", "HY 신용스프레드", round(credit.value()), "%",
                    creditZone(credit.value()), null, clamp(credit.value() / 8 * 100)));
        }
        if (vix != null) {
            risk.add(Indicator.of("vix", "VIX", round(vix), null,
                    vixZone(vix), "변동성 지수 (단일 지표 해석은 주의)", vixPosition(vix)));
        }

        List<Indicator> macro = new ArrayList<>();
        if (yc2 != null) {
            macro.add(Indicator.of("yieldCurve2y", "장단기 금리차(10Y-2Y)", round(yc2.value()), "%p",
                    yieldZone(yc2.value()), yieldNote(yc2.value()), clamp((yc2.value() + 2) / 5 * 100)));
        }
        if (yc3 != null) {
            macro.add(Indicator.of("yieldCurve3m", "장단기 금리차(10Y-3M)", round(yc3.value()), "%p",
                    yieldZone(yc3.value()), yieldNote(yc3.value()), clamp((yc3.value() + 2) / 5 * 100)));
        }
        if (unemp != null) {
            macro.add(Indicator.of("unemployment", "실업률", round(unemp.value()), "%",
                    unemploymentZone(unemp.value()), "경기 지표 (상승 전환 시 둔화 신호)",
                    clamp((unemp.value() - 3) / 7 * 100)));
        }
        if (netLiq != null) {
            macro.add(Indicator.of("netLiquidity", "순유동성", round(netLiq), "조$",
                    "neutral", "Fed 순유동성 (최근 2년 범위 내 위치)", netLiquidityPosition(netLiq)));
        }

        List<Indicator> trend = new ArrayList<>();
        if (sp200dev != null) {
            trend.add(Indicator.of("sp500vs200ma", "S&P500 vs 200일선", round(sp200dev), "%",
                    sp200Zone(sp200dev), "200일 이동평균 대비 이격도", clamp((sp200dev + 25) / 50 * 100)));
        }

        // composite: 과열도 정규화 평균 (버핏·Fear&Greed·신용·200일선)
        List<Double> norms = new ArrayList<>();
        if (buffett != null) norms.add(clamp((buffett - 80) / (220 - 80) * 100));
        if (fg != null) norms.add(clamp(fg.score()));
        if (credit != null) norms.add(clamp((5 - credit.value()) / (5 - 2) * 100));
        if (sp200dev != null) norms.add(clamp(50 + sp200dev * 2.5));   // +20%→100, -20%→0
        Composite composite = norms.isEmpty() ? null : toComposite(avg(norms));

        // 분기(최근 3개월) 섹터·테마 모멘텀 — 자체 캐시. 실패 시 빈 리스트(국면 응답은 정상 제공).
        List<SectorMomentum> sectors;
        try {
            sectors = sectorService.getQuarterlyMomentum();
        } catch (Exception ex) {
            log.warn("quarterly sector momentum failed: {}", ex.getMessage());
            sectors = List.of();
        }
        List<SectorMomentum> themes;
        try {
            themes = sectorService.getQuarterlyThemes();
        } catch (Exception ex) {
            log.warn("quarterly theme momentum failed: {}", ex.getMessage());
            themes = List.of();
        }

        return new MarketRegimeResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                composite,
                new Axes(new Axis(valuation), new Axis(risk), new Axis(macro), new Axis(trend)),
                sectors,
                themes,
                DISCLAIMER
        );
    }

    /** 실시간 VIX 현재가(대시보드와 동일 소스: FMP ^VIX → Yahoo). 둘 다 실패 시 null(호출부가 FRED로 폴백). */
    private Double liveVix() {
        Quote q = tryQuote(() -> fmpClient.quote("^VIX"));
        if (q == null) q = tryQuote(() -> yahooFinanceClient.quote("^VIX"));
        return (q != null && q.price() != null) ? q.price().doubleValue() : null;
    }

    /** 시세 조회 래퍼: 유효한 가격(양수)만 반환, 실패/예외 시 null. */
    private Quote tryQuote(Supplier<Quote> supplier) {
        try {
            Quote q = supplier.get();
            if (q != null && q.price() != null && q.price().signum() > 0) return q;
        } catch (Exception ex) {
            log.debug("vix quote fallback: {}", ex.getMessage());
        }
        return null;
    }

    /** 순유동성(조$) = WALCL[백만$] - WTREGEN[백만$] - RRPONTSYD[십억$ → 백만 ×1000]. */
    private Double netLiquidity() {
        FredObservation walcl = fred.latestValue("WALCL");
        FredObservation tga = fred.latestValue("WTREGEN");
        FredObservation rrp = fred.latestValue("RRPONTSYD");
        if (walcl == null || tga == null || rrp == null) return null;
        double millions = walcl.value() - tga.value() - rrp.value() * 1000;
        return millions / 1_000_000.0;
    }

    /**
     * 순유동성의 최근 2년 역사적 범위 대비 현재 위치(0~100).
     * WALCL(주간) 날짜에 같은 날짜의 TGA·RRP를 매칭해 순유동성 시계열을 구성, min/max 대비 위치 산출.
     * 데이터 부족 시 null(게이지 생략).
     */
    private Double netLiquidityPosition(double current) {
        List<FredObservation> walcl = fred.latestSeries("WALCL", 110);   // ~2년 주간
        if (walcl.size() < 20) return null;
        Map<String, Double> tgaMap = new HashMap<>();
        for (FredObservation o : fred.latestSeries("WTREGEN", 760)) tgaMap.put(o.date(), o.value());
        Map<String, Double> rrpMap = new HashMap<>();
        for (FredObservation o : fred.latestSeries("RRPONTSYD", 760)) rrpMap.put(o.date(), o.value());

        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        int matched = 0;
        for (FredObservation w : walcl) {
            Double t = tgaMap.get(w.date());
            Double r = rrpMap.get(w.date());
            if (t == null || r == null) continue;
            double nl = (w.value() - t - r * 1000) / 1_000_000.0;
            min = Math.min(min, nl);
            max = Math.max(max, nl);
            matched++;
        }
        if (matched < 10 || max <= min) return null;
        return clamp((current - min) / (max - min) * 100);
    }

    /** S&P500 200일 이동평균 대비 현재가 이격도(%). 데이터 부족 시 null. */
    private Double sp500DeviationFrom200dma() {
        List<FredObservation> sp = fred.latestSeries("SP500", 300);
        if (sp.size() < 200) return null;
        double current = sp.get(0).value();
        double ma200 = sp.subList(0, 200).stream().mapToDouble(FredObservation::value).average().orElse(0);
        if (ma200 <= 0) return null;
        return (current - ma200) / ma200 * 100;
    }

    private String generateAiSummary(MarketRegimeResponse regime) {
        if (regime.composite() == null) return null;
        try {
            LlmClient.LlmResult result = llm.generate(
                    promptLoader.load(AI_PROMPT_FILE), buildAiPrompt(regime), LlmMetrics.FEATURE_MARKET_REGIME);
            JsonNode node = MAPPER.readTree(result.content());
            String summary = node.path("summary").asText(null);
            return (summary == null || summary.isBlank()) ? null : summary;
        } catch (Exception ex) {
            log.warn("market-regime ai summary failed: {}", ex.getMessage());
            return null;
        }
    }

    private static String buildAiPrompt(MarketRegimeResponse r) {
        StringBuilder sb = new StringBuilder("현재 미국 시장 국면 지표:\n");
        if (r.composite() != null) {
            sb.append("- 종합 국면: ").append(r.composite().score())
                    .append("/100 (").append(r.composite().labelKo()).append(")\n");
        }
        appendAxis(sb, r.axes().valuation());
        appendAxis(sb, r.axes().riskSentiment());
        appendAxis(sb, r.axes().macro());
        appendAxis(sb, r.axes().trendBreadth());
        appendMomentum(sb, "최근 3개월 섹터 수익률(강세→약세 정렬)", r.sectors(), 0);
        appendMomentum(sb, "최근 3개월 테마 수익률(강세 상위·약세 하위)", r.themes(), 5);
        sb.append("\n위 지표를 해석하세요.");
        return sb.toString();
    }

    /**
     * 분기(최근 3개월) 모멘텀을 프롬프트에 제공. 강세→약세 정렬 가정.
     * topBottom>0이면 상위·하위 각 N개만(테마처럼 항목이 많을 때 프롬프트 절약).
     */
    private static void appendMomentum(StringBuilder sb, String title, List<SectorMomentum> list, int topBottom) {
        if (list == null || list.isEmpty()) return;
        List<SectorMomentum> show;
        if (topBottom > 0 && list.size() > topBottom * 2) {
            show = new ArrayList<>();
            show.addAll(list.subList(0, topBottom));
            show.addAll(list.subList(list.size() - topBottom, list.size()));
        } else {
            show = list;
        }
        sb.append("\n").append(title).append(":\n");
        for (SectorMomentum s : show) {
            sb.append("- ").append(s.sectorKo()).append(": ")
                    .append(s.returnPct() >= 0 ? "+" : "").append(s.returnPct()).append("%\n");
        }
    }

    private static void appendAxis(StringBuilder sb, Axis axis) {
        if (axis == null || axis.indicators() == null) return;
        for (Indicator i : axis.indicators()) {
            sb.append("- ").append(i.name()).append(": ").append(i.value());
            if (i.unit() != null) sb.append(i.unit());
            sb.append(" (").append(i.zone()).append(")\n");
        }
    }

    // ── zone 정규화 (design §3) ──

    private static String buffettZone(double v) {
        if (v < 100) return "cheap";
        if (v <= 140) return "normal";
        if (v <= 180) return "caution";   // 다소 고평가 (현대 구조적 고평가 구간 등급화)
        return "overheated";
    }

    private static String buffettNote(double v) {
        if (v > 180) return "역사적 평균 대비 크게 높은 과열 구간";
        if (v > 140) return "역사적 평균 대비 다소 높은 고평가 구간";
        if (v < 100) return "역사적 평균 대비 저평가 구간";
        return "역사적 평균 부근";
    }

    private static String fearGreedZone(double v) {
        if (v < 45) return "fear";
        if (v <= 55) return "neutral";
        return "greed";
    }

    private static String creditZone(double v) {
        if (v < 3) return "calm";
        if (v <= 5) return "normal";
        return "fear";
    }

    private static String vixZone(double v) {
        if (v < 15) return "calm";
        if (v <= 20) return "normal";   // VIX 20 = 평온↔변동성 확대 통상 경계선
        return "alarm";                 // >20 불안 — 한 방향 경고(red)
    }

    /** VIX 마커를 3개 zone 칸(균등 1/3)에 정렬: &lt;15 안정 / 15~20 정상 / &gt;20 불안(40 상한). */
    private static double vixPosition(double v) {
        double third = 100.0 / 3;
        if (v < 15) return clamp(v / 15 * third);
        if (v <= 20) return third + (v - 15) / 5 * third;
        return clamp(2 * third + (v - 20) / 20 * third);
    }

    private static String yieldZone(double v) {
        if (v < 0) return "inverted";
        if (v <= 0.5) return "neutral";
        return "normal";
    }

    private static String yieldNote(double v) {
        if (v < 0) return "금리 역전 — 과거 경기 침체에 선행한 사례가 있음 (확정적 예측 아님)";
        return "정상(우상향) 구간";
    }

    private static String sp200Zone(double dev) {
        if (dev > 2) return "uptrend";      // 200일선 +2% 초과 = 상승추세
        if (dev < -2) return "downtrend";   // -2% 미만 = 하락추세
        return "neutral";                   // ±2% 이내 = 횡보(추세 불명확) — 경계 깜빡임 방지
    }

    private static String unemploymentZone(double v) {
        if (v < 4) return "low";
        if (v <= 5) return "normal";
        return "elevated";
    }

    // ── composite ──

    private static Composite toComposite(double score) {
        int s = (int) Math.round(score);
        String label;
        String labelKo;
        if (s < 30) { label = "fear"; labelKo = "공포(저평가 쪽)"; }
        else if (s < 45) { label = "cautious"; labelKo = "조심"; }
        else if (s <= 55) { label = "neutral"; labelKo = "중립"; }
        else if (s <= 70) { label = "greed"; labelKo = "탐욕(과열 쪽)"; }
        else { label = "overheated"; labelKo = "과열"; }
        return new Composite(s, label, labelKo);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }

    private static double avg(List<Double> xs) {
        return xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
