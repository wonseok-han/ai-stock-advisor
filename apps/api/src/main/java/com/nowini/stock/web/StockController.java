package com.nowini.stock.web;

import com.nowini.common.error.BusinessException;
import com.nowini.common.error.ErrorCode;
import com.nowini.sec.domain.SecFiling;
import com.nowini.sec.service.SecFilingService;
import com.nowini.stock.domain.AnalystEstimates;
import com.nowini.stock.domain.Candle;
import com.nowini.stock.domain.CompanyOverview;
import com.nowini.stock.domain.IndicatorSnapshot;
import com.nowini.stock.domain.Quote;
import com.nowini.stock.domain.SearchHit;
import com.nowini.stock.domain.StockDetailResponse;
import com.nowini.stock.domain.StockProfile;
import com.nowini.stock.domain.TimeFrame;
import com.nowini.stock.service.AnalystEstimatesService;
import com.nowini.stock.service.CandleService;
import com.nowini.stock.service.CompanyOverviewService;
import com.nowini.stock.service.IndicatorService;
import com.nowini.stock.service.QuoteService;
import com.nowini.stock.service.SearchService;
import com.nowini.stock.service.StockDetailService;
import com.nowini.stock.service.StockProfileService;
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
    private final CandleService candleService;
    private final IndicatorService indicatorService;
    private final StockDetailService detailService;
    private final CompanyOverviewService overviewService;
    private final AnalystEstimatesService analystService;
    private final SecFilingService secFilingService;

    public StockController(SearchService searchService,
                           StockProfileService profileService,
                           QuoteService quoteService,
                           CandleService candleService,
                           IndicatorService indicatorService,
                           StockDetailService detailService,
                           CompanyOverviewService overviewService,
                           AnalystEstimatesService analystService,
                           SecFilingService secFilingService) {
        this.searchService = searchService;
        this.profileService = profileService;
        this.quoteService = quoteService;
        this.candleService = candleService;
        this.indicatorService = indicatorService;
        this.detailService = detailService;
        this.overviewService = overviewService;
        this.analystService = analystService;
        this.secFilingService = secFilingService;
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

    @GetMapping("/{ticker}/candles")
    public List<Candle> candles(
            @PathVariable("ticker") @Pattern(regexp = TICKER_REGEX) String ticker,
            @RequestParam(value = "tf", defaultValue = "1D") String tf) {
        TimeFrame frame = TimeFrame.fromCode(tf);
        if (frame == null) {
            throw new BusinessException(ErrorCode.INVALID_TICKER,
                    "지원하지 않는 timeframe: " + tf);
        }
        return candleService.getCandles(ticker, frame);
    }

    @GetMapping("/{ticker}/indicators")
    public IndicatorSnapshot indicators(
            @PathVariable("ticker") @Pattern(regexp = TICKER_REGEX) String ticker) {
        return indicatorService.compute(ticker);
    }

    @GetMapping("/{ticker}/overview")
    public CompanyOverview overview(
            @PathVariable("ticker") @Pattern(regexp = TICKER_REGEX) String ticker) {
        return overviewService.getOverview(ticker);
    }

    @GetMapping("/{ticker}/analyst")
    public AnalystEstimates analyst(
            @PathVariable("ticker") @Pattern(regexp = TICKER_REGEX) String ticker) {
        return analystService.getEstimates(ticker);
    }

    @GetMapping("/{ticker}/sec-filings")
    public List<SecFiling> secFilings(
            @PathVariable("ticker") @Pattern(regexp = TICKER_REGEX) String ticker) {
        return secFilingService.getRecentFilings(ticker, 5).stream()
                .map(f -> new SecFiling(f.ticker(), f.form(), f.title(), f.filedAt(),
                        f.eventCategory(), f.daysAgo(), f.documentUrl(), null, f.summaryKo(),
                        f.sentiment()))
                .toList();
    }

    @GetMapping("/{ticker}/detail")
    public StockDetailResponse detail(
            @PathVariable("ticker") @Pattern(regexp = TICKER_REGEX) String ticker,
            @RequestParam(value = "tf", defaultValue = "1D") String tf) {
        TimeFrame frame = TimeFrame.fromCode(tf);
        if (frame == null) {
            throw new BusinessException(ErrorCode.INVALID_TICKER,
                    "지원하지 않는 timeframe: " + tf);
        }
        return detailService.getDetail(ticker, frame);
    }
}
