package com.nowini.ai.web;

import com.nowini.ai.domain.AiSignal;
import com.nowini.ai.service.AiSignalService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stocks")
@Validated
public class AiSignalController {

    private static final String TICKER_REGEX = "^[A-Z]{1,5}(\\.[A-Z])?$";

    private final AiSignalService service;

    public AiSignalController(AiSignalService service) {
        this.service = service;
    }

    @GetMapping("/{ticker}/ai-signal")
    public AiSignal signal(
            @PathVariable("ticker") @Pattern(regexp = TICKER_REGEX) String ticker) {
        return service.getSignal(ticker);
    }
}
