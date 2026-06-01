package com.nowini.market.web;

import com.nowini.market.domain.MarketNewsItem;
import com.nowini.market.domain.MarketOverviewResponse;
import com.nowini.market.domain.SectorPerformance;
import com.nowini.market.service.MarketNewsService;
import com.nowini.market.service.MarketOverviewService;
import com.nowini.market.service.SectorPerformanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market")
public class MarketController {

    private final MarketOverviewService overviewService;
    private final MarketNewsService newsService;
    private final SectorPerformanceService sectorService;

    public MarketController(MarketOverviewService overviewService,
                            MarketNewsService newsService,
                            SectorPerformanceService sectorService) {
        this.overviewService = overviewService;
        this.newsService = newsService;
        this.sectorService = sectorService;
    }

    @GetMapping("/overview")
    public MarketOverviewResponse overview() {
        return overviewService.getOverview();
    }

    @GetMapping("/news")
    public List<MarketNewsItem> news(
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "before", required = false) Long before) {
        return newsService.getNews(limit, before);
    }

    @GetMapping("/sectors")
    public List<SectorPerformance> sectors() {
        return sectorService.getSectors();
    }
}
