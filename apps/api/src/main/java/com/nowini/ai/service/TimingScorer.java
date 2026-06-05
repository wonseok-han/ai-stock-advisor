package com.nowini.ai.service;

import com.nowini.ai.domain.TimingFactor;
import com.nowini.ai.domain.TimingVerdict;
import com.nowini.ai.domain.TimingVerdict.Verdict;
import com.nowini.stock.domain.AnalystEstimates;
import com.nowini.stock.domain.IndicatorSnapshot;
import com.nowini.stock.domain.IndicatorSnapshot.MovingAverage;
import com.nowini.stock.domain.Quote;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 진입 타이밍을 <b>단기/장기 두 관점</b>으로 결정론 계산한다.
 * <p>
 * LLM이 아니라 코드가 ✓/✗ 를 판정하므로 같은 데이터면 항상 같은 결과(재현성)이고,
 * "장기로는 진입 우위, 단기로는 아직" 처럼 관점별 판정이 분리돼 나온다. LLM은 perspectives(해석)만 담당.
 * <ul>
 *   <li>단기(1~2주): RSI/볼린저/단기 이평(20·50일) 지지/거래량/MACD 모멘텀 — 눌림목·단기 반등 관점.</li>
 *   <li>장기(6개월~1년): 장기 이평(120·200일) 지지/밸류에이션/52주 저점/정배열 — 추세선 지지·저평가 관점.</li>
 * </ul>
 * 이평 "지지"는 한참 위(과열)가 아니라 <b>해당 선에 근접(±%)</b>해야 충족 — 눌림목/반등 자리를 잡기 위함.
 */
@Component
public class TimingScorer {

    private static final String DISCLAIMER =
            "진입 조건의 기술적 충족 여부를 정리한 것으로, 투자 판단은 본인의 책임입니다.";

    private static final double SHORT_MA_TOL = 0.03; // 단기 이평 근접 ±3%
    private static final double LONG_MA_TOL = 0.05;  // 장기 이평 근접 ±5%

    // 단기 가중치 (합 100)
    private static final int W_RSI = 20, W_BOLL = 15, W_SMA_S = 20, W_VOL = 15, W_MACD = 15, W_VIX_S = 15;
    // 장기 가중치 (합 100)
    private static final int W_SMA_L = 25, W_VAL = 25, W_LOW52 = 20, W_ALIGN = 10, W_MA60 = 10, W_VIX_L = 10;

    /** 단기 진입(1~2주): 눌림목·단기 반등 관점. */
    public TimingVerdict scoreShort(Quote quote, IndicatorSnapshot ind, BigDecimal vix) {
        List<TimingFactor> met = new ArrayList<>();
        List<TimingFactor> unmet = new ArrayList<>();

        evalRsi(ind, met, unmet);
        evalBollinger(ind, met, unmet);
        evalShortMaSupport(quote, ind, met, unmet);
        evalVolume(quote, ind, met, unmet);
        evalMacd(ind, met, unmet);
        evalVix(vix, W_VIX_S, met, unmet);

        return verdictOf(met, unmet, "단기");
    }

    /** 장기 진입(6개월~1년): 장기 추세선 지지·저평가 관점. */
    public TimingVerdict scoreLong(Quote quote, IndicatorSnapshot ind,
                                   AnalystEstimates analyst, BigDecimal vix) {
        List<TimingFactor> met = new ArrayList<>();
        List<TimingFactor> unmet = new ArrayList<>();

        evalLongMaSupport(quote, ind, met, unmet);
        evalValuation(analyst, met, unmet);
        eval52wLow(quote, met, unmet);
        evalAlignment(ind, met, unmet);
        evalMa60Trend(quote, ind, met, unmet);
        evalVix(vix, W_VIX_L, met, unmet);

        return verdictOf(met, unmet, "장기");
    }

    private TimingVerdict verdictOf(List<TimingFactor> met, List<TimingFactor> unmet, String horizon) {
        int score = met.stream().mapToInt(TimingFactor::weight).sum(); // 만점 100
        Verdict verdict = score >= 70 ? Verdict.NOW
                : score >= 40 ? Verdict.UNCERTAIN : Verdict.NOT_YET;
        int total = met.size() + unmet.size();
        return new TimingVerdict(verdict, score, met, unmet,
                summaryOf(horizon, verdict, total, met.size(), score), DISCLAIMER);
    }

    // ── 단기 팩터 ────────────────────────────────────────────────

    // RSI 과매도 (≤30)
    private void evalRsi(IndicatorSnapshot ind, List<TimingFactor> met, List<TimingFactor> unmet) {
        if (ind == null) { unmet.add(missing("RSI 과매도", W_RSI, "RSI 데이터")); return; }
        double rsi = ind.rsi14();
        add(rsi <= 30, "RSI 과매도", W_RSI, met, unmet,
                String.format("RSI %.1f로 과매도 구간(≤30)입니다.", rsi),
                String.format("RSI %.1f로 과매도 구간이 아닙니다.", rsi));
    }

    // 볼린저밴드 하단 (%B ≤ 0.1)
    private void evalBollinger(IndicatorSnapshot ind, List<TimingFactor> met, List<TimingFactor> unmet) {
        if (ind == null || ind.bollinger() == null) { unmet.add(missing("볼린저밴드 하단", W_BOLL, "볼린저밴드 데이터")); return; }
        double pb = ind.bollinger().percentB();
        add(pb <= 0.1, "볼린저밴드 하단", W_BOLL, met, unmet,
                String.format("볼린저 %%B %.2f로 밴드 하단에 근접합니다.", pb),
                String.format("볼린저 %%B %.2f로 밴드 하단이 아닙니다.", pb));
    }

    // 단기 이평 지지 (현재가가 20·50일선에 근접 ±3%)
    private void evalShortMaSupport(Quote quote, IndicatorSnapshot ind, List<TimingFactor> met, List<TimingFactor> unmet) {
        Double price = priceOf(quote);
        MovingAverage ma = ind == null ? null : ind.movingAverage();
        if (price == null || ma == null) { unmet.add(missing("단기 이평 지지", W_SMA_S, "단기 이동평균/현재가")); return; }
        boolean s20 = supported(price, ma.ma20(), SHORT_MA_TOL);
        boolean s50 = supported(price, ma.ma50(), SHORT_MA_TOL);
        if (s20 || s50) {
            String which = s20 ? "20일선" : "50일선";
            met.add(new TimingFactor("단기 이평 지지",
                    String.format("현재가가 %s 부근에서 지지받는 눌림목 구간입니다.", which), W_SMA_S));
        } else {
            unmet.add(new TimingFactor("단기 이평 지지",
                    "현재가가 20·50일선 지지 구간에서 벗어나 있습니다.", W_SMA_S));
        }
    }

    // 거래량 급증 (당일 거래량 ≥ 20일 평균 × 2)
    private void evalVolume(Quote quote, IndicatorSnapshot ind, List<TimingFactor> met, List<TimingFactor> unmet) {
        if (quote == null || quote.volume() <= 0 || ind == null || ind.avgVolume20d() <= 0) {
            unmet.add(missing("거래량 급증", W_VOL, "거래량/20일 평균"));
            return;
        }
        double ratio = (double) quote.volume() / ind.avgVolume20d();
        add(ratio >= 2.0, "거래량 급증", W_VOL, met, unmet,
                String.format("거래량이 20일 평균의 %.1f배로 급증했습니다.", ratio),
                String.format("거래량이 20일 평균의 %.1f배 수준입니다.", ratio));
    }

    // MACD 모멘텀 (히스토그램 > 0)
    private void evalMacd(IndicatorSnapshot ind, List<TimingFactor> met, List<TimingFactor> unmet) {
        if (ind == null || ind.macd() == null) { unmet.add(missing("MACD 모멘텀", W_MACD, "MACD 데이터")); return; }
        double h = ind.macd().histogram();
        add(h > 0, "MACD 모멘텀", W_MACD, met, unmet,
                String.format("MACD 히스토그램이 양수(%+.3f)로 상승 모멘텀입니다.", h),
                String.format("MACD 히스토그램이 음수(%+.3f)로 상승 모멘텀이 약합니다.", h));
    }

    // ── 장기 팩터 ────────────────────────────────────────────────

    // 장기 이평 지지 (현재가가 200·120일선에 근접 ±5%)
    private void evalLongMaSupport(Quote quote, IndicatorSnapshot ind, List<TimingFactor> met, List<TimingFactor> unmet) {
        Double price = priceOf(quote);
        MovingAverage ma = ind == null ? null : ind.movingAverage();
        if (price == null || ma == null) { unmet.add(missing("장기 이평 지지", W_SMA_L, "장기 이동평균/현재가")); return; }
        if (ma.ma200() == null && ma.ma120() == null) {
            unmet.add(missing("장기 이평 지지", W_SMA_L, "120·200일 이동평균"));
            return;
        }
        boolean s200 = supported(price, ma.ma200(), LONG_MA_TOL);
        boolean s120 = supported(price, ma.ma120(), LONG_MA_TOL);
        if (s200 || s120) {
            String which = s200 ? "200일선" : "120일선";
            met.add(new TimingFactor("장기 이평 지지",
                    String.format("현재가가 %s 부근에서 지지받는 장기 추세선 구간입니다.", which), W_SMA_L));
        } else {
            unmet.add(new TimingFactor("장기 이평 지지",
                    "현재가가 120·200일선 지지 구간에서 벗어나 있습니다.", W_SMA_L));
        }
    }

    // 밸류에이션 (애널리스트 목표가 상승여력 ≥ 20%)
    private void evalValuation(AnalystEstimates analyst, List<TimingFactor> met, List<TimingFactor> unmet) {
        if (analyst == null || analyst.priceTarget() == null || analyst.priceTarget().upsidePercent() == null) {
            unmet.add(missing("밸류에이션", W_VAL, "애널리스트 목표가"));
            return;
        }
        double upside = analyst.priceTarget().upsidePercent().doubleValue();
        add(upside >= 20, "밸류에이션", W_VAL, met, unmet,
                String.format("애널리스트 목표가 상승여력이 %+.1f%%로 밸류에이션 매력이 있습니다.", upside),
                String.format("애널리스트 목표가 상승여력이 %+.1f%%로 밸류에이션 매력은 제한적입니다.", upside));
    }

    // 52주 저점 근접 (현재가 ≤ 저점 × 1.10)
    private void eval52wLow(Quote quote, List<TimingFactor> met, List<TimingFactor> unmet) {
        if (quote == null || quote.price() == null || quote.week52Low() == null
                || quote.week52Low().signum() <= 0) {
            unmet.add(missing("52주 저점 근접", W_LOW52, "52주 저점/현재가"));
            return;
        }
        double price = quote.price().doubleValue();
        double low = quote.week52Low().doubleValue();
        double pct = (price / low - 1) * 100;
        add(pct <= 10, "52주 저점 근접", W_LOW52, met, unmet,
                String.format("현재가가 52주 저점 대비 %+.1f%%로 저점에 근접합니다.", pct),
                String.format("현재가가 52주 저점 대비 %+.1f%%로 저점과 거리가 있습니다.", pct));
    }

    // 정배열 (MA60 > MA120)
    private void evalAlignment(IndicatorSnapshot ind, List<TimingFactor> met, List<TimingFactor> unmet) {
        MovingAverage ma = ind == null ? null : ind.movingAverage();
        if (ma == null || ma.ma120() == null) { unmet.add(missing("정배열", W_ALIGN, "120일 이동평균")); return; }
        double ma60 = ma.ma60();
        double ma120 = ma.ma120();
        add(ma60 > ma120, "정배열", W_ALIGN, met, unmet,
                String.format("60일선이 120일선(%.2f) 위에 있어 중기 정배열입니다.", ma120),
                String.format("60일선이 120일선(%.2f) 아래로 중기 추세가 약합니다.", ma120));
    }

    // 이동평균 추세 지지 (현재가 > MA60)
    private void evalMa60Trend(Quote quote, IndicatorSnapshot ind, List<TimingFactor> met, List<TimingFactor> unmet) {
        Double price = priceOf(quote);
        MovingAverage ma = ind == null ? null : ind.movingAverage();
        if (price == null || ma == null || ma.ma60() <= 0) {
            unmet.add(missing("이동평균 추세", W_MA60, "60일 이동평균"));
            return;
        }
        double ma60 = ma.ma60();
        add(price > ma60, "이동평균 추세", W_MA60, met, unmet,
                String.format("현재가가 60일선(%.2f) 위에 있어 추세 지지가 확인됩니다.", ma60),
                String.format("현재가가 60일선(%.2f) 아래에 있습니다.", ma60));
    }

    // 시장 안정성 (VIX < 20) — 가중치는 관점별로 다름
    private void evalVix(BigDecimal vix, int weight, List<TimingFactor> met, List<TimingFactor> unmet) {
        if (vix == null) { unmet.add(missing("시장 안정성", weight, "VIX")); return; }
        double v = vix.doubleValue();
        add(v < 20, "시장 안정성", weight, met, unmet,
                String.format("VIX %.1f로 시장이 안정적(<20)입니다.", v),
                String.format("VIX %.1f로 시장 변동성이 높은 편입니다.", v));
    }

    // ── helpers ─────────────────────────────────────────────────

    /** 현재가가 해당 이평선에 ±tol 이내로 근접하면 지지 구간으로 본다(과열 추격 배제). */
    private static boolean supported(double price, Double ma, double tol) {
        return ma != null && ma > 0 && Math.abs(price / ma - 1) <= tol;
    }

    private static Double priceOf(Quote quote) {
        return quote == null || quote.price() == null ? null : quote.price().doubleValue();
    }

    private void add(boolean isMet, String factor, int weight,
                     List<TimingFactor> met, List<TimingFactor> unmet,
                     String metDetail, String unmetDetail) {
        if (isMet) met.add(new TimingFactor(factor, metDetail, weight));
        else unmet.add(new TimingFactor(factor, unmetDetail, weight));
    }

    private TimingFactor missing(String factor, int weight, String what) {
        return new TimingFactor(factor, what + "가 없어 확인하지 못했습니다.", weight);
    }

    private String summaryOf(String horizon, Verdict verdict, int total, int metCount, int score) {
        String head = switch (verdict) {
            case NOW -> horizon + " 진입 조건이 뚜렷이 충족된 구간입니다";
            case UNCERTAIN -> horizon + " 진입 조건이 일부만 충족돼 판단이 엇갈리는 구간입니다";
            case NOT_YET -> horizon + " 진입 조건 충족도가 낮은 구간입니다";
        };
        return String.format("%d개 조건 중 %d개 충족(%d점) — %s.", total, metCount, score, head);
    }
}
