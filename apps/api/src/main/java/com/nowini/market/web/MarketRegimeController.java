package com.nowini.market.web;

import com.nowini.market.domain.MarketRegimeResponse;
import com.nowini.market.service.MarketRegimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시장 국면 종합 API.
 * <p>
 * {@code GET /api/v1/market/regime} — 공개(지표 + composite).
 * AI 해석은 별도 인증 엔드포인트 {@code /regime/ai}(후속 Step)에서 제공.
 */
@RestController
@RequestMapping("/api/v1/market")
public class MarketRegimeController {

    private final MarketRegimeService regimeService;

    public MarketRegimeController(MarketRegimeService regimeService) {
        this.regimeService = regimeService;
    }

    @GetMapping("/regime")
    public MarketRegimeResponse regime() {
        return regimeService.getRegime();
    }
}
