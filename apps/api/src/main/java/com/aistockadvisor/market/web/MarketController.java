package com.aistockadvisor.market.web;

import com.aistockadvisor.market.domain.MarketMoversResponse;
import com.aistockadvisor.market.domain.MarketNewsItem;
import com.aistockadvisor.market.domain.MarketOverviewResponse;
import com.aistockadvisor.market.service.MarketMoversService;
import com.aistockadvisor.market.service.MarketNewsService;
import com.aistockadvisor.market.service.MarketOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 시장 대시보드 REST 엔드포인트.
 * 참조: docs/02-design/features/market-dashboard.design.md §4
 */
@RestController
@RequestMapping("/api/v1/market")
public class MarketController {

    private final MarketOverviewService overviewService;
    private final MarketNewsService newsService;
    private final MarketMoversService moversService;

    public MarketController(MarketOverviewService overviewService,
                            MarketNewsService newsService,
                            MarketMoversService moversService) {
        this.overviewService = overviewService;
        this.newsService = newsService;
        this.moversService = moversService;
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
}
