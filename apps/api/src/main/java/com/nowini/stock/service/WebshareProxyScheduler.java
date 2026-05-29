package com.nowini.stock.service;

import com.nowini.stock.infra.client.WebshareProxyClient;
import com.nowini.stock.infra.client.YahooFinanceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Webshare 프록시 리스트를 주기적으로 갱신해 {@link YahooFinanceClient}의 프록시 풀에 반영한다.
 * <p>
 * 앱 시작 직후 1회 + {@code app.yahoo.webshare.refresh-interval}(기본 1시간) 간격으로 실행.
 * Webshare가 비활성(API 키 없음)이거나 응답이 비면 기존 풀을 유지한다.
 */
@Component
public class WebshareProxyScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebshareProxyScheduler.class);

    private final WebshareProxyClient webshare;
    private final YahooFinanceClient yahooFinance;

    public WebshareProxyScheduler(WebshareProxyClient webshare, YahooFinanceClient yahooFinance) {
        this.webshare = webshare;
        this.yahooFinance = yahooFinance;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${app.yahoo.webshare.refresh-interval:PT1H}")
    public void refreshProxies() {
        if (!webshare.isEnabled()) return;

        List<String> proxies = webshare.fetchProxyUrls();
        if (proxies.isEmpty()) {
            log.warn("webshare: empty proxy list — keeping existing pool");
            return;
        }
        yahooFinance.refreshProxyPool(proxies);
    }
}
