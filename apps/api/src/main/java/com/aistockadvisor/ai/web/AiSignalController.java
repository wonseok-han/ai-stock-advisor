package com.aistockadvisor.ai.web;

import com.aistockadvisor.ai.domain.AiSignal;
import com.aistockadvisor.ai.service.AiSignalService;
import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import com.aistockadvisor.stock.domain.TimeFrame;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 시그널 REST 엔드포인트.
 * 참조: docs/02-design/features/phase2-rag-pipeline.design.md §4
 *
 * <p>GET /api/v1/ai-signal?ticker=AAPL&tf=1D
 */
@RestController
@RequestMapping("/api/v1/ai-signal")
@Validated
public class AiSignalController {

    private static final String TICKER_REGEX = "^[A-Z]{1,5}(\\.[A-Z])?$";

    private final AiSignalService service;

    public AiSignalController(AiSignalService service) {
        this.service = service;
    }

    @GetMapping
    public AiSignal signal(
            @RequestParam("ticker") @Pattern(regexp = TICKER_REGEX) String ticker,
            @RequestParam(value = "tf", defaultValue = "1D") String tf) {
        TimeFrame frame;
        try {
            frame = TimeFrame.fromCode(tf);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_TICKER, "지원하지 않는 timeframe: " + tf, null);
        }
        return service.getSignal(ticker, frame);
    }
}
