package com.aistockadvisor.bookmark.service;

import com.aistockadvisor.bookmark.domain.BookmarkListResponse;
import com.aistockadvisor.bookmark.domain.BookmarkResponse;
import com.aistockadvisor.bookmark.infra.BookmarkEntity;
import com.aistockadvisor.bookmark.infra.BookmarkRepository;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.infra.StockSymbolEntity;
import com.aistockadvisor.stock.infra.StockSymbolRepository;
import com.aistockadvisor.stock.service.QuoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class BookmarkService {

    private static final Logger log = LoggerFactory.getLogger(BookmarkService.class);

    private final BookmarkRepository bookmarkRepo;
    private final StockSymbolRepository symbolRepo;
    private final QuoteService quoteService;

    public BookmarkService(BookmarkRepository bookmarkRepo,
                           StockSymbolRepository symbolRepo,
                           QuoteService quoteService) {
        this.bookmarkRepo = bookmarkRepo;
        this.symbolRepo = symbolRepo;
        this.quoteService = quoteService;
    }

    @Transactional
    public BookmarkResponse add(UUID userId, String ticker) {
        String upper = ticker.toUpperCase();
        if (bookmarkRepo.existsByUserIdAndTicker(userId, upper)) {
            // 멱등: 이미 존재하면 기존 것 반환
            BookmarkEntity existing = bookmarkRepo.findByUserIdAndTicker(userId, upper).orElseThrow();
            return toResponse(existing);
        }
        BookmarkEntity entity = bookmarkRepo.save(new BookmarkEntity(userId, upper));
        return toResponse(entity);
    }

    @Transactional
    public void remove(UUID userId, String ticker) {
        bookmarkRepo.deleteByUserIdAndTicker(userId, ticker.toUpperCase());
    }

    @Transactional(readOnly = true)
    public BookmarkListResponse list(UUID userId) {
        List<BookmarkEntity> entities = bookmarkRepo.findByUserIdOrderByCreatedAtDesc(userId);
        List<BookmarkResponse> items = entities.stream()
                .map(this::toResponseWithQuote)
                .toList();
        return new BookmarkListResponse(items, items.size());
    }

    @Transactional(readOnly = true)
    public boolean check(UUID userId, String ticker) {
        return bookmarkRepo.existsByUserIdAndTicker(userId, ticker.toUpperCase());
    }

    private BookmarkResponse toResponse(BookmarkEntity entity) {
        String name = symbolRepo.findById(entity.getTicker())
                .map(StockSymbolEntity::getDescription)
                .orElse(entity.getTicker());
        return new BookmarkResponse(entity.getTicker(), name, null, null, entity.getCreatedAt());
    }

    private BookmarkResponse toResponseWithQuote(BookmarkEntity entity) {
        String name = symbolRepo.findById(entity.getTicker())
                .map(StockSymbolEntity::getDescription)
                .orElse(entity.getTicker());
        try {
            Quote quote = quoteService.getQuote(entity.getTicker());
            return new BookmarkResponse(
                    entity.getTicker(), name,
                    quote.price(), quote.changePercent(),
                    entity.getCreatedAt());
        } catch (Exception e) {
            log.warn("Failed to fetch quote for bookmark {}: {}", entity.getTicker(), e.getMessage());
            return new BookmarkResponse(entity.getTicker(), name, BigDecimal.ZERO, BigDecimal.ZERO, entity.getCreatedAt());
        }
    }
}
