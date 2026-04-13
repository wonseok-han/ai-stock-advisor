package com.aistockadvisor.stock.service;

import java.util.Map;

/**
 * 지표 한국어 툴팁 (참고용 문구). 추후 i18n 도입 시 분리.
 * 참조: docs/02-design/features/mvp.design.md §3.5 (line 377).
 */
final class IndicatorTooltips {

    static final Map<String, String> KO = Map.of(
            "rsi14",     "RSI 14일. 70 이상 과매수, 30 이하 과매도 경향(확정 신호 아님).",
            "macd",      "MACD = 단기(12)와 장기(26) 이동평균의 차. histogram 양전환은 모멘텀 회복 초기 신호.",
            "bollinger", "Bollinger Bands(20, 2σ). %B 1.0 = 상단, 0.0 = 하단. 변동성 + 가격 위치.",
            "ma",        "단순 이동평균(5/20/60일). 단기·중기·장기 추세 비교."
    );

    private IndicatorTooltips() {
    }
}
