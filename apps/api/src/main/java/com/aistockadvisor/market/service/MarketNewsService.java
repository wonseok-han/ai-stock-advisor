package com.aistockadvisor.market.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.legal.Disclaimers;
import com.aistockadvisor.market.domain.MarketNewsItem;
import com.aistockadvisor.market.infra.FinnhubMarketNewsClient;
import com.aistockadvisor.news.infra.FinnhubNewsClient.CompanyNews;
import com.aistockadvisor.news.service.NewsTranslator;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 시장 일반 뉴스. Redis 15분 캐시 (DB 미사용).
 * 참조: docs/02-design/features/market-dashboard.design.md §5.2
 */
@Service
public class MarketNewsService {

    private static final Logger log = LoggerFactory.getLogger(MarketNewsService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final TypeReference<List<MarketNewsItem>> TYPE = new TypeReference<>() {
    };
    private static final int DEFAULT_LIMIT = 10;

    private final FinnhubMarketNewsClient newsClient;
    private final NewsTranslator translator;
    private final RedisCacheAdapter cache;

    public MarketNewsService(FinnhubMarketNewsClient newsClient,
                             NewsTranslator translator,
                             RedisCacheAdapter cache) {
        this.newsClient = newsClient;
        this.translator = translator;
        this.cache = cache;
    }

    public List<MarketNewsItem> getNews(int limit) {
        int take = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, DEFAULT_LIMIT);
        List<MarketNewsItem> all = cache.getOrLoad("market:news", TYPE, CACHE_TTL, this::fetchAndTranslate);
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        return all.size() <= take ? all : all.subList(0, take);
    }

    private List<MarketNewsItem> fetchAndTranslate() {
        List<CompanyNews> raw = newsClient.fetchGeneralNews();
        if (raw.isEmpty()) {
            return List.of();
        }

        List<MarketNewsItem> items = new ArrayList<>(raw.size());
        for (CompanyNews news : raw) {
            String titleKo = null;
            String summaryKo = null;

            try {
                NewsTranslator.Translation tr = translator.translate(news);
                if (tr != null) {
                    titleKo = tr.titleKo();
                    summaryKo = tr.summaryKo();
                }
            } catch (Exception ex) {
                log.debug("market news translation failed id={}: {}", news.id(), ex.getMessage());
            }

            items.add(new MarketNewsItem(
                    news.id(),
                    news.source() != null ? news.source() : "Unknown",
                    news.url(),
                    news.headline(),
                    titleKo,
                    summaryKo,
                    news.datetime(),
                    Disclaimers.MARKET_NEWS
            ));
        }
        return items;
    }
}
