package com.nowini.market.service;

import com.nowini.stock.domain.MarketStatus;
import com.nowini.stock.domain.MarketStatusResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대시보드 캐시 워밍 스케줄러.
 * <p>
 * 사용자 요청이 콜드패스(캐시 만료 시점에 외부 API를 동기 호출하며 대기)를 만나지 않도록,
 * 캐시 만료 전에 미리 데이터를 받아 캐시를 덮어쓴다(읽기 경로 getOrLoad는 그대로). 워밍 실패 시
 * 기존 캐시가 그대로 살아 있어 공백이 없다. 뉴스(이벤트성·알림 전담)와 regime AI(비싼 LLM,
 * 로그인 전용)는 워밍 대상에서 제외한다.
 * <p>
 * 주기는 각 캐시 TTL보다 짧게(마진 확보) 잡되 FMP 250/day 한도를 고려한다.
 */
@Component
public class MarketWarmupScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketWarmupScheduler.class);

    private final MarketOverviewService overviewService;
    private final SectorPerformanceService sectorService;
    private final MarketRegimeService regimeService;

    public MarketWarmupScheduler(MarketOverviewService overviewService,
                                 SectorPerformanceService sectorService,
                                 MarketRegimeService regimeService) {
        this.overviewService = overviewService;
        this.sectorService = sectorService;
        this.regimeService = regimeService;
    }

    /**
     * overview + 섹터 퍼포먼스: 매시 정각·30분(벽시계 기준), 장중에만. 각 TTL 40분 대비 10분 마진
     * (30분마다 갱신이 항상 만료보다 먼저 → 빈틈 없음). cron 이라 배포와 무관하게 시각 고정.
     * 장 마감 시간대에는 데이터가 정적이고 FMP 한도를 아끼기 위해 워밍하지 않는다(읽기 경로가 처리).
     */
    @Scheduled(cron = "0 0,30 * * * *")
    public void warmIntraday() {
        if (MarketStatusResolver.resolve() != MarketStatus.OPEN) return;
        warm("overview", overviewService::refresh);
        warm("sectors", sectorService::refresh);
    }

    /** 섹터·테마 분기 모멘텀: 6시간마다(TTL 12h 대비 마진). Yahoo 캔들 31종이라 자주 돌리지 않는다. */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000)
    public void warmQuarterly() {
        warm("momentum", sectorService::refreshQuarterly);
    }

    /** 국면 지표: 미 증시 정규장 마감 직후(ET 16:30, 월~금) 1회. close 기반 TTL과 정렬. */
    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "America/New_York")
    public void warmRegime() {
        warm("regime", regimeService::refresh);
    }

    private void warm(String name, Runnable task) {
        try {
            task.run();
            log.debug("warmup ok: {}", name);
        } catch (Exception ex) {
            log.warn("warmup failed: {} reason={}", name, ex.getMessage());
        }
    }
}
