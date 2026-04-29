package com.nowini.market.web;

import com.nowini.market.domain.MarketMoversResponse;
import com.nowini.market.domain.MarketNewsItem;
import com.nowini.market.domain.MarketOverviewResponse;
import com.nowini.market.domain.SectorPerformance;
import com.nowini.market.service.MarketMoversService;
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
    private final MarketMoversService moversService;
    private final SectorPerformanceService sectorService;

    public MarketController(MarketOverviewService overviewService,
                            MarketNewsService newsService,
                            MarketMoversService moversService,
                            SectorPerformanceService sectorService) {
        this.overviewService = overviewService;
        this.newsService = newsService;
        this.moversService = moversService;
        this.sectorService = sectorService;
    }

    @GetMapping("/overview")
    public MarketOverviewResponse overview() {
        return overviewService.getOverview();
    }

    @GetMapping("/news")
    public List<MarketNewsItem> news(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return newsService.getNews(limit);
    }

    @GetMapping("/movers")
    public MarketMoversResponse movers() {
        return moversService.getMovers();
    }

    @GetMapping("/sectors")
    public List<SectorPerformance> sectors() {
        return sectorService.getSectors();
    }
}
