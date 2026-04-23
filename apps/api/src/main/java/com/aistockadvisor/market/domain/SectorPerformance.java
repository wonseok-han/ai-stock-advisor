package com.aistockadvisor.market.domain;

public record SectorPerformance(
        String sector,
        String sectorKo,
        Double changePercent
) {
}
