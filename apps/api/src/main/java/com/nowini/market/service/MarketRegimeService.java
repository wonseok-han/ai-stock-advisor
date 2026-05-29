package com.nowini.market.service;

import com.nowini.cache.RedisCacheAdapter;
import com.nowini.market.domain.MarketRegimeResponse;
import com.nowini.market.domain.MarketRegimeResponse.Axes;
import com.nowini.market.domain.MarketRegimeResponse.Axis;
import com.nowini.market.domain.MarketRegimeResponse.Composite;
import com.nowini.market.domain.MarketRegimeResponse.Indicator;
import com.nowini.market.infra.CnnFearGreedClient;
import com.nowini.market.infra.CnnFearGreedClient.FearGreedSnapshot;
import com.nowini.market.infra.FredClient;
import com.nowini.market.infra.FredClient.FredObservation;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 시장 국면 종합 — FRED(버핏지수·금리차·신용스프레드) + CNN(Fear&Greed)를 4축으로 수집·정규화.
 * <p>
 * MVP: 밸류에이션/위험심리/매크로 3축. 추세폭(200일선·RSP/SPY)은 후속.
 * composite(0=공포/저평가 ~ 100=과열/고평가)는 과열도가 명확한 지표(버핏·Fear&Greed·신용)를 정규화 평균.
 * 부분 실패 허용 — 일부 소스 실패해도 나머지로 composite 산출. 면책 문구 동반.
 * <p>
 * 참조: docs/02-design/features/market-regime.design.md
 */
@Service
public class MarketRegimeService {

    private static final Duration TTL = Duration.ofHours(6);
    private static final TypeReference<MarketRegimeResponse> TYPE = new TypeReference<>() {};
    private static final String DISCLAIMER =
            "본 지표는 투자 자문이 아닌 정보 제공·참고용입니다. 투자 판단과 책임은 사용자 본인에게 있습니다.";

    private final FredClient fred;
    private final CnnFearGreedClient cnn;
    private final RedisCacheAdapter cache;

    public MarketRegimeService(FredClient fred, CnnFearGreedClient cnn, RedisCacheAdapter cache) {
        this.fred = fred;
        this.cnn = cnn;
        this.cache = cache;
    }

    public MarketRegimeResponse getRegime() {
        return cache.getOrLoad("market:regime", TYPE, TTL, this::fetch);
    }

    private MarketRegimeResponse fetch() {
        // ── 밸류에이션: 버핏지수 = (시총 NCBEILQ027S[백만$]/1000 → 십억$) / GDP[십억$] × 100 ──
        FredObservation ncb = fred.latestValue("NCBEILQ027S");
        FredObservation gdp = fred.latestValue("GDP");
        Double buffett = (ncb != null && gdp != null && gdp.value() > 0)
                ? (ncb.value() / 1000.0) / gdp.value() * 100 : null;

        // ── 위험·심리: Fear&Greed + 신용스프레드 ──
        FearGreedSnapshot fg = cnn.fetch();
        FredObservation credit = fred.latestValue("BAMLH0A0HYM2");

        // ── 매크로: 장단기 금리차 ──
        FredObservation yieldCurve = fred.latestValue("T10Y2Y");

        // 지표 조립
        List<Indicator> valuation = new ArrayList<>();
        if (buffett != null) {
            valuation.add(Indicator.of("buffett", "버핏지수", round(buffett), "%",
                    buffettZone(buffett), buffettNote(buffett)));
        }

        List<Indicator> risk = new ArrayList<>();
        if (fg != null) {
            risk.add(new Indicator("fearGreed", "공포·탐욕 지수", round(fg.score()), null,
                    fearGreedZone(fg.score()), null,
                    fg.prev1Week(), fg.prev1Month(), fg.prev1Year()));
        }
        if (credit != null) {
            risk.add(Indicator.of("creditSpread", "HY 신용스프레드", round(credit.value()), "%",
                    creditZone(credit.value()), null));
        }

        List<Indicator> macro = new ArrayList<>();
        if (yieldCurve != null) {
            macro.add(Indicator.of("yieldCurve", "장단기 금리차(10Y-2Y)", round(yieldCurve.value()), "%p",
                    yieldZone(yieldCurve.value()), yieldNote(yieldCurve.value())));
        }

        // composite: 과열도 정규화 평균 (가용 지표만)
        List<Double> norms = new ArrayList<>();
        if (buffett != null) norms.add(clamp((buffett - 80) / (220 - 80) * 100));     // 80%→0, 220%→100
        if (fg != null) norms.add(clamp(fg.score()));                                 // 탐욕=과열
        if (credit != null) norms.add(clamp((5 - credit.value()) / (5 - 2) * 100));   // 낮은 스프레드(안일)=과열, 2%→100/5%→0
        Composite composite = norms.isEmpty() ? null : toComposite(avg(norms));

        return new MarketRegimeResponse(
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                composite,
                new Axes(new Axis(valuation), new Axis(risk), new Axis(macro), new Axis(List.of())),
                DISCLAIMER
        );
    }

    // ── zone 정규화 (design §3) ──

    private static String buffettZone(double v) {
        if (v < 100) return "cheap";
        if (v <= 150) return "normal";
        return "overheated";
    }

    private static String buffettNote(double v) {
        if (v > 200) return "역사적 평균 대비 크게 높은 고평가 구간";
        if (v > 150) return "역사적 평균 대비 고평가 구간";
        if (v < 100) return "역사적 평균 대비 저평가 구간";
        return "역사적 평균 부근";
    }

    private static String fearGreedZone(double v) {
        if (v < 25) return "fear";
        if (v < 45) return "fear";
        if (v <= 55) return "neutral";
        if (v <= 75) return "greed";
        return "greed";
    }

    private static String creditZone(double v) {
        if (v < 3) return "calm";
        if (v <= 5) return "normal";
        return "fear";
    }

    private static String yieldZone(double v) {
        if (v < 0) return "inverted";
        if (v <= 0.5) return "neutral";
        return "normal";
    }

    private static String yieldNote(double v) {
        if (v < 0) return "장단기 금리 역전 — 과거 경기 침체에 선행한 사례가 있음 (확정적 예측 아님)";
        return "정상(우상향) 구간";
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
