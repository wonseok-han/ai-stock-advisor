package com.aistockadvisor.stock.service;

import java.util.Map;

/**
 * 지표 한국어 툴팁 (참고용 문구). 추후 i18n 도입 시 분리.
 * 참조: docs/02-design/features/mvp.design.md §3.5 (line 377).
 */
final class IndicatorTooltips {

    static final Map<String, String> KO = Map.of(
            "rsi14",
            "최근 14일 동안의 상승/하락 힘을 0~100 점수로 본 지표입니다. "
                    + "70을 넘으면 '단기 과열', 30 아래면 '단기 침체' 경향이지만, "
                    + "매수·매도 신호는 아닙니다.",
            "macd",
            "단기(12일) 평균과 장기(26일) 평균의 차이를 보며 가격 흐름의 힘을 읽는 지표입니다. "
                    + "아래 Hist 막대가 0 위로 올라오면 상승 흐름 시작, "
                    + "0 아래로 내려가면 하락 흐름 시작 가능성을 의미합니다.",
            "bollinger",
            "가격이 20일 평균에서 얼마나 벌어져 움직이는지를 보여주는 지표입니다. "
                    + "%B는 현재 위치를 숫자로 표현하며 1.0=상단선, 0.5=중앙선, 0.0=하단선입니다. "
                    + "1에 가까우면 단기 과열, 0에 가까우면 단기 침체 신호일 수 있습니다.",
            "ma",
            "MA(이동평균)는 최근 5·20·60일 동안의 평균 가격입니다. "
                    + "단기선(5)이 장기선(60) 위에 있으면 상승 추세, "
                    + "아래에 있으면 하락 추세로 참고합니다."
    );

    private IndicatorTooltips() {
    }
}
