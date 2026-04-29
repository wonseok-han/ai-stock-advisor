package com.nowini.stock.service;

import com.nowini.cache.RedisCacheAdapter;
import com.nowini.market.infra.FmpClient;
import com.nowini.market.infra.FmpClient.FmpProfile;
import com.nowini.market.infra.FmpClient.FmpRatiosTtm;
import com.nowini.stock.domain.CompanyOverview;
import com.nowini.stock.infra.client.YahooFinanceClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class CompanyOverviewService {

    private static final Logger log = LoggerFactory.getLogger(CompanyOverviewService.class);
    private static final Duration TTL = Duration.ofHours(24);
    private static final TypeReference<CompanyOverview> TYPE = new TypeReference<>() {
    };

    private final YahooFinanceClient yahooClient;
    private final FmpClient fmpClient;
    private final RedisCacheAdapter cache;

    public CompanyOverviewService(YahooFinanceClient yahooClient, FmpClient fmpClient, RedisCacheAdapter cache) {
        this.yahooClient = yahooClient;
        this.fmpClient = fmpClient;
        this.cache = cache;
    }

    public CompanyOverview getOverview(String ticker) {
        return cache.getOrLoad("overview:" + ticker, TYPE, TTL,
                () -> fetchWithFallback(ticker));
    }

    private CompanyOverview fetchWithFallback(String ticker) {
        CompanyOverview overview = yahooClient.quoteSummary(ticker);
        if (overview != null) return overview;

        log.debug("yahoo quoteSummary fallback to FMP for {}", ticker);
        return fetchFromFmp(ticker);
    }

    private CompanyOverview fetchFromFmp(String ticker) {
        FmpProfile profile = fmpClient.companyProfile(ticker);
        if (profile == null) return null;

        FmpRatiosTtm ratios = fmpClient.ratiosTtm(ticker);

        return new CompanyOverview(
                profile.sector(),
                profile.industry(),
                profile.mktCap() != null ? BigDecimal.valueOf(profile.mktCap()) : null,
                dbl(ratios != null ? ratios.peRatio() : null),
                dbl(ratios != null ? ratios.eps() : null),
                dbl(ratios != null && ratios.dividendPerShare() != null
                        ? ratios.dividendPerShare() : profile.lastDiv()),
                dbl(profile.beta()),
                null,
                null,
                profile.description(),
                profile.fullTimeEmployees(),
                profile.website(),
                profile.ipoDate()
        );
    }

    private static BigDecimal dbl(Double v) {
        return v != null ? BigDecimal.valueOf(v) : null;
    }
}
