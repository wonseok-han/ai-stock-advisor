package com.aistockadvisor.stock.web;

import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.domain.SearchHit;
import com.aistockadvisor.stock.domain.StockProfile;
import com.aistockadvisor.stock.service.QuoteService;
import com.aistockadvisor.stock.service.SearchService;
import com.aistockadvisor.stock.service.StockProfileService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 종목 검색/마스터/시세 REST 엔드포인트.
 * 입력 검증: design §5.6 (ticker 정규식, query 길이).
 */
@RestController
@RequestMapping("/api/v1/stocks")
@Validated
public class StockController {

    /** Ticker 화이트리스트: 1~5 대문자 + 옵션 .X (BRK.B 등). */
    private static final String TICKER_REGEX = "^[A-Z]{1,5}(\\.[A-Z])?$";

    private final SearchService searchService;
    private final StockProfileService profileService;
    private final QuoteService quoteService;

    public StockController(SearchService searchService,
                           StockProfileService profileService,
                           QuoteService quoteService) {
        this.searchService = searchService;
        this.profileService = profileService;
        this.quoteService = quoteService;
    }

    @GetMapping("/search")
    public List<SearchHit> search(
            @RequestParam("q") @NotBlank @Size(max = 20) String query) {
        return searchService.search(query);
    }

    @GetMapping("/{ticker}/profile")
    public StockProfile profile(
            @PathVariable("ticker") @Pattern(regexp = TICKER_REGEX) String ticker) {
        return profileService.getProfile(ticker);
    }

    @GetMapping("/{ticker}/quote")
    public Quote quote(
            @PathVariable("ticker") @Pattern(regexp = TICKER_REGEX) String ticker) {
        return quoteService.getQuote(ticker);
    }
}
