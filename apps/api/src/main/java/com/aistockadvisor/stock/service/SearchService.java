package com.aistockadvisor.stock.service;

import com.aistockadvisor.cache.RedisCacheAdapter;
import com.aistockadvisor.stock.domain.SearchHit;
import com.aistockadvisor.stock.infra.client.FinnhubClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 종목 검색. Finnhub SymbolLookup 결과를 정제.
 * Cache: search:{query} (1h, design §3.4).
 */
@Service
public class SearchService {

    private static final Duration TTL = Duration.ofHours(1);
    private static final int MAX_RESULTS = 10;
    private static final TypeReference<List<SearchHit>> LIST_TYPE = new TypeReference<>() {
    };

    private final FinnhubClient finnhub;
    private final RedisCacheAdapter cache;

    public SearchService(FinnhubClient finnhub, RedisCacheAdapter cache) {
        this.finnhub = finnhub;
        this.cache = cache;
    }

    public List<SearchHit> search(String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        String key = "search:" + normalized;
        return cache.getOrLoad(key, LIST_TYPE, TTL, () -> finnhub.search(normalized).stream()
                .filter(hit -> "Common Stock".equalsIgnoreCase(hit.type()) || hit.type() == null)
                .map(hit -> new SearchHit(hit.symbol(), hit.description(), null, "ticker"))
                .sorted(Comparator.comparing(SearchHit::ticker))
                .limit(MAX_RESULTS)
                .toList());
    }
}
