package com.nowini.market.domain;

/**
 * 섹터 분기 모멘텀 — 최근 3개월(롤링) 섹터 ETF 누적 수익률(%).
 * <p>
 * 당일 등락(SectorPerformance)과 달리 분기 단위 추세(섹터 로테이션)를 나타낸다.
 * returnPct 양수=강세, 음수=약세. Yahoo Finance 일봉 adjClose 기반 실데이터.
 */
public record SectorMomentum(
        String sector,
        String sectorKo,
        Double returnPct
) {}
