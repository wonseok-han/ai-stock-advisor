package com.aistockadvisor.stock.service;

import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import com.aistockadvisor.stock.domain.Candle;
import com.aistockadvisor.stock.domain.IndicatorSnapshot;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.domain.StockDetailResponse;
import com.aistockadvisor.stock.domain.StockDetailResponse.BlockError;
import com.aistockadvisor.stock.domain.StockDetailResponse.Disclaimer;
import com.aistockadvisor.stock.domain.StockDetailResponse.Meta;
import com.aistockadvisor.stock.domain.StockProfile;
import com.aistockadvisor.stock.domain.TimeFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 종목 상세 통합 서비스 (design §4.2).
 * Java 21 virtual threads 로 profile/quote/candles/indicators 병렬 호출,
 * 블록별 실패는 partial 응답으로 흡수. news/aiSignal 은 Phase 2 에서 채움.
 */
@Service
public class StockDetailService {

    private static final Logger log = LoggerFactory.getLogger(StockDetailService.class);

    // docs/planning/07-legal-compliance.md 기반 v1.0 기본 고지 문구.
    private static final Disclaimer DEFAULT_DISCLAIMER = new Disclaimer(
            "/stock/{ticker}",
            "v1.0",
            "본 서비스는 투자 자문이 아닌 분석 도구입니다. 모든 투자 판단과 책임은 사용자 본인에게 있습니다."
    );

    private final StockProfileService profileService;
    private final QuoteService quoteService;
    private final CandleService candleService;
    private final IndicatorService indicatorService;

    public StockDetailService(StockProfileService profileService,
                              QuoteService quoteService,
                              CandleService candleService,
                              IndicatorService indicatorService) {
        this.profileService = profileService;
        this.quoteService = quoteService;
        this.candleService = candleService;
        this.indicatorService = indicatorService;
    }

    public StockDetailResponse getDetail(String ticker, TimeFrame tf) {
        List<BlockError> errors = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<StockProfile> profileF = executor.submit(() -> profileService.getProfile(ticker));
            Future<Quote> quoteF = executor.submit(() -> quoteService.getQuote(ticker));
            Future<List<Candle>> candlesF = executor.submit(() -> candleService.getCandles(ticker, tf));
            Future<IndicatorSnapshot> indF = executor.submit(() -> indicatorService.compute(ticker));

            StockProfile profile = await("profile", profileF, errors);
            Quote quote = await("quote", quoteF, errors);
            List<Candle> candles = await("candles", candlesF, errors);
            IndicatorSnapshot indicators = await("indicators", indF, errors);

            // Profile 과 Quote 모두 실패면 ticker 자체가 무효 → 404 로 통일.
            if (profile == null && quote == null) {
                throw new BusinessException(ErrorCode.TICKER_NOT_FOUND);
            }

            Disclaimer disclaimer = new Disclaimer(
                    "/stock/" + ticker,
                    DEFAULT_DISCLAIMER.version(),
                    DEFAULT_DISCLAIMER.text()
            );

            return new StockDetailResponse(
                    profile,
                    quote,
                    candles,
                    indicators,
                    null,  // news: Phase 2
                    null,  // aiSignal: Phase 2
                    disclaimer,
                    !errors.isEmpty(),
                    errors.isEmpty() ? List.of() : List.copyOf(errors),
                    new Meta(
                            "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                            OffsetDateTime.now(ZoneOffset.UTC)
                    )
            );
        }
    }

    private static <T> T await(String block, Future<T> future, List<BlockError> errors) {
        try {
            return future.get();
        } catch (ExecutionException ex) {
            return recordBlockFailure(block, ex.getCause(), errors);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            errors.add(new BlockError(block, ErrorCode.UPSTREAM_TIMEOUT.name(), "interrupted"));
            return null;
        }
    }

    private static <T> T recordBlockFailure(String block, Throwable cause, List<BlockError> errors) {
        if (cause instanceof BusinessException be) {
            errors.add(new BlockError(block, be.code().name(), safeMessage(be)));
            log.debug("detail block '{}' failed: {} - {}", block, be.code(), be.getMessage());
        } else {
            errors.add(new BlockError(block, ErrorCode.INTERNAL_ERROR.name(),
                    cause == null ? "unknown" : cause.getMessage()));
            log.warn("detail block '{}' failed with unexpected error", block, cause);
        }
        return null;
    }

    private static String safeMessage(BusinessException ex) {
        String msg = ex.getMessage();
        return msg == null || msg.isBlank() ? ex.code().defaultMessage() : msg;
    }
}
