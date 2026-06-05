package com.nowini.stock.service;

import com.nowini.ai.infra.GeminiLlmClient;
import com.nowini.stock.infra.client.WebshareProxyClient;
import com.nowini.stock.infra.client.YahooFinanceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Webshare 프록시 리스트를 주기적으로 갱신해 외부 API 클라이언트에 반영한다.
 * <p>
 * - {@link YahooFinanceClient}: 프록시 풀 hot-swap (429 회피, 로테이션용)
 * - {@link GeminiLlmClient}: US 프록시 1개 (위치 차단 "User location is not supported" 우회)
 * <p>
 * 앱 시작 직후 1회 + {@code app.yahoo.webshare.refresh-interval}(기본 1시간) 간격 실행.
 * Webshare 비활성(API 키 없음) 시 둘 다 기존 설정을 유지한다.
 */
@Component
@Profile("!local & !test")   // 로컬·테스트에선 스케줄러 끔 (운영과 동시 실행 시 중복 호출 방지)
public class WebshareProxyScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebshareProxyScheduler.class);

    private final WebshareProxyClient webshare;
    private final YahooFinanceClient yahooFinance;
    private final GeminiLlmClient gemini;

    public WebshareProxyScheduler(WebshareProxyClient webshare,
                                  YahooFinanceClient yahooFinance,
                                  GeminiLlmClient gemini) {
        this.webshare = webshare;
        this.yahooFinance = yahooFinance;
        this.gemini = gemini;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${app.yahoo.webshare.refresh-interval:PT1H}")
    public void refreshProxies() {
        if (!webshare.isEnabled()) return;

        // Yahoo: 프록시 풀 전체 갱신
        List<String> proxies = webshare.fetchProxyUrls();
        if (!proxies.isEmpty()) {
            yahooFinance.refreshProxyPool(proxies);
        } else {
            log.warn("webshare: empty proxy list — keeping existing yahoo pool");
        }

        // Gemini: US 프록시 1개 (위치 차단 우회)
        String usProxy = webshare.fetchUsProxyUrl();
        if (usProxy != null) {
            gemini.refreshProxy(usProxy);
        } else {
            log.warn("webshare: no US proxy available for gemini");
        }
    }
}
