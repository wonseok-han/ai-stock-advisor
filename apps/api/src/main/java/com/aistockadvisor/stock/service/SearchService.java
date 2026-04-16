package com.aistockadvisor.stock.service;

import com.aistockadvisor.stock.domain.SearchHit;
import com.aistockadvisor.stock.infra.StockSymbolRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 종목 검색. DB stock_symbol 테이블 로컬 검색 (외부 API 호출 없음).
 * pg_trgm + prefix 매치로 심볼/종목명 검색.
 */
@Service
public class SearchService {

    private static final int MAX_RESULTS = 10;

    private final StockSymbolRepository symbolRepository;

    public SearchService(StockSymbolRepository symbolRepository) {
        this.symbolRepository = symbolRepository;
    }

    public List<SearchHit> search(String query) {
        String normalized = query.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        return symbolRepository.search(normalized, MAX_RESULTS).stream()
                .map(e -> new SearchHit(e.getSymbol(), e.getDescription(), null, "ticker"))
                .toList();
    }
}
