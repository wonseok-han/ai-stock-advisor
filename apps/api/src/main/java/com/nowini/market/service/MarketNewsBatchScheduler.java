package com.nowini.market.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시장 뉴스 수집 배치 스케줄러.
 * <p>
 * 매시 정각·30분(벽시계 기준)에 Finnhub general news 를 수집·번역해 DB(market_news)에 적재한다.
 * 뉴스는 장외에도 발행되므로 장 시간대 게이트를 두지 않는다(24/7). cron 이라 배포와 무관하게 시각 고정.
 */
@Component
public class MarketNewsBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketNewsBatchScheduler.class);

    private final MarketNewsService newsService;

    public MarketNewsBatchScheduler(MarketNewsService newsService) {
        this.newsService = newsService;
    }

    @Scheduled(cron = "0 0,30 * * * *")
    public void batch() {
        try {
            newsService.refresh();
            log.debug("market news batch ok");
        } catch (Exception ex) {
            log.warn("market news batch failed: {}", ex.getMessage());
        }
    }
}
