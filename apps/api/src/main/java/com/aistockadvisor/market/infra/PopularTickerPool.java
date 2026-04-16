package com.aistockadvisor.market.infra;

import java.util.List;

/**
 * 급등/급락 산출용 인기 종목 풀.
 * Finnhub 무료에 movers API 없으므로 이 풀에서 quote 일괄 조회 후 변동률 정렬.
 * 참조: docs/02-design/features/market-dashboard.design.md §4.3
 *
 * <p>시가총액 상위 + 섹터 다양성 기준 선정 (30개).
 */
public final class PopularTickerPool {

    public static final List<String> TICKERS = List.of(
            // Mega Cap Tech
            "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "META", "TSLA",
            // Semiconductor
            "AMD", "INTC", "AVGO", "QCOM",
            // Finance
            "JPM", "BAC", "GS", "V", "MA",
            // Healthcare
            "UNH", "JNJ", "PFE", "ABBV",
            // Consumer
            "WMT", "COST", "NKE", "DIS",
            // Energy
            "XOM", "CVX",
            // Industrial
            "BA", "CAT",
            // Communication
            "NFLX", "CRM"
    );

    private PopularTickerPool() {
    }
}
