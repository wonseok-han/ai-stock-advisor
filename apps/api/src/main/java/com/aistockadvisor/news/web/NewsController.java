package com.aistockadvisor.news.web;

import com.aistockadvisor.news.domain.NewsItem;
import com.aistockadvisor.news.service.NewsService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 뉴스 REST 엔드포인트.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §4
 *
 * <p>GET /api/v1/news?ticker=AAPL&limit=5
 */
@RestController
@RequestMapping("/api/v1/news")
@Validated
public class NewsController {

    private static final String TICKER_REGEX = "^[A-Z]{1,5}(\\.[A-Z])?$";

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public List<NewsItem> list(
            @RequestParam("ticker") @Pattern(regexp = TICKER_REGEX) String ticker,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return newsService.getNews(ticker, limit);
    }
}
