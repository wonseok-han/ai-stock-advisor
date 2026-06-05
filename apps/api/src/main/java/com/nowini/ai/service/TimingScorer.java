package com.nowini.ai.service;

import com.nowini.ai.domain.TimingFactor;
import com.nowini.ai.domain.TimingVerdict;
import com.nowini.ai.domain.TimingVerdict.Verdict;
import com.nowini.stock.domain.AnalystEstimates;
import com.nowini.stock.domain.IndicatorSnapshot;
import com.nowini.stock.domain.Quote;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 진입 타이밍 8팩터를 측정 지표로 <b>결정론적</b> 계산한다.
 * <p>
 * LLM이 아니라 코드가 ✓/✗ 를 판정하므로 같은 데이터면 항상 같은 결과(재현성)이고,
 * 충족/미충족 체크리스트로 "지금이니?!"가 명확히 드러난다. LLM은 perspectives(해석)만 담당.
 * 임계값/가중치는 ai-signal 프롬프트의 8팩터 규칙을 그대로 코드로 옮긴 것.
 */
@Component
public class TimingScorer {

    private static final String DISCLAIMER =
            "진입 조건의 기술적 충족 여부를 정리한 것으로, 투자 판단은 본인의 책임입니다.";

    private static final int W_RSI = 15, W_MACD = 15, W_BOLL = 10, W_LOW52 = 15,
            W_MA60 = 10, W_VOL = 10, W_VIX = 10, W_VAL = 15;

    public TimingVerdict score(Quote quote, IndicatorSnapshot ind,
                               AnalystEstimates analyst, BigDecimal vix) {
        List<TimingFactor> met = new ArrayList<>();
        List<TimingFactor> unmet = new ArrayList<>();

        evalRsi(ind, met, unmet);
        evalMacd(ind, met, unmet);
        evalBollinger(ind, met, unmet);
        eval52wLow(quote, met, unmet);
        evalMa60(quote, ind, met, unmet);
        evalVolume(quote, ind, met, unmet);
        evalVix(vix, met, unmet);
        evalValuation(analyst, met, unmet);

        int score = met.stream().mapToInt(TimingFactor::weight).sum(); // 만점 100
        Verdict verdict = score >= 70 ? Verdict.NOW
                : score >= 40 ? Verdict.UNCERTAIN : Verdict.NOT_YET;
        return new TimingVerdict(verdict, score, met, unmet, summaryOf(verdict, met.size(), score), DISCLAIMER);
    }

    // 1. RSI 과매도 (≤30)
    private void evalRsi(IndicatorSnapshot ind, List<TimingFactor> met, List<TimingFactor> unmet) {
        if (ind == null) { unmet.add(missing("RSI 과매도", W_RSI, "RSI 데이터")); return; }
        double rsi = ind.rsi14();
        add(rsi <= 30, "RSI 과매도", W_RSI, met, unmet,
                String.format("RSI %.1f로 과매도 구간(≤30)입니다.", rsi),
                String.format("RSI %.1f로 과매도 구간이 아닙니다.", rsi));
    }

    // 2. MACD 모멘텀 (히스토그램 > 0 = 시그널선 상회)
    private void evalMacd(IndicatorSnapshot ind, List<TimingFactor> met, List<TimingFactor> unmet) {
        if (ind == null || ind.macd() == null) { unmet.add(missing("MACD 모멘텀", W_MACD, "MACD 데이터")); return; }
        double h = ind.macd().histogram();
        add(h > 0, "MACD 모멘텀", W_MACD, met, unmet,
                String.format("MACD 히스토그램이 양수(%+.3f)로 상승 모멘텀입니다.", h),
                String.format("MACD 히스토그램이 음수(%+.3f)로 상승 모멘텀이 약합니다.", h));
    }

    // 3. 볼린저밴드 하단 (%B ≤ 0.1)
    private void evalBollinger(IndicatorSnapshot ind, List<TimingFactor> met, List<TimingFactor> unmet) {
        if (ind == null || ind.bollinger() == null) { unmet.add(missing("볼린저밴드 하단", W_BOLL, "볼린저밴드 데이터")); return; }
        double pb = ind.bollinger().percentB();
        add(pb <= 0.1, "볼린저밴드 하단", W_BOLL, met, unmet,
                String.format("볼린저 %%B %.2f로 밴드 하단에 근접합니다.", pb),
                String.format("볼린저 %%B %.2f로 밴드 하단이 아닙니다.", pb));
    }

    // 4. 52주 저점 근접 (현재가 ≤ 저점 × 1.10)
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

    // 5. 이동평균 지지 (현재가 > MA60)
    private void evalMa60(Quote quote, IndicatorSnapshot ind, List<TimingFactor> met, List<TimingFactor> unmet) {
        if (quote == null || quote.price() == null || ind == null || ind.movingAverage() == null
                || ind.movingAverage().ma60() <= 0) {
            unmet.add(missing("이동평균 지지", W_MA60, "60일 이동평균"));
            return;
        }
        double price = quote.price().doubleValue();
        double ma60 = ind.movingAverage().ma60();
        add(price > ma60, "이동평균 지지", W_MA60, met, unmet,
                String.format("현재가가 60일선(%.2f) 위에 있어 추세 지지가 확인됩니다.", ma60),
                String.format("현재가가 60일선(%.2f) 아래에 있습니다.", ma60));
    }

    // 6. 거래량 급증 (당일 거래량 ≥ 20일 평균 × 2)
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

    // 7. 시장 안정성 (VIX < 20)
    private void evalVix(BigDecimal vix, List<TimingFactor> met, List<TimingFactor> unmet) {
        if (vix == null) { unmet.add(missing("시장 안정성", W_VIX, "VIX")); return; }
        double v = vix.doubleValue();
        add(v < 20, "시장 안정성", W_VIX, met, unmet,
                String.format("VIX %.1f로 시장이 안정적(<20)입니다.", v),
                String.format("VIX %.1f로 시장 변동성이 높은 편입니다.", v));
    }

    // 8. 밸류에이션 (애널리스트 목표가 상승여력 ≥ 20%)
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

    private void add(boolean isMet, String factor, int weight,
                     List<TimingFactor> met, List<TimingFactor> unmet,
                     String metDetail, String unmetDetail) {
        if (isMet) met.add(new TimingFactor(factor, metDetail, weight));
        else unmet.add(new TimingFactor(factor, unmetDetail, weight));
    }

    private TimingFactor missing(String factor, int weight, String what) {
        return new TimingFactor(factor, what + "가 없어 확인하지 못했습니다.", weight);
    }

    private String summaryOf(Verdict verdict, int metCount, int score) {
        String head = switch (verdict) {
            case NOW -> "기술적 진입 조건이 뚜렷이 충족된 구간입니다";
            case UNCERTAIN -> "진입 조건이 일부만 충족돼 판단이 엇갈리는 구간입니다";
            case NOT_YET -> "진입 조건 충족도가 낮은 구간입니다";
        };
        return String.format("8개 조건 중 %d개 충족(%d점) — %s.", metCount, score, head);
    }
}
