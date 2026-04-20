package com.aistockadvisor.notification.service;

import com.aistockadvisor.news.domain.NewsItem;
import com.aistockadvisor.news.service.NewsService;
import com.aistockadvisor.notification.config.NotificationDedupProperties;
import com.aistockadvisor.notification.infra.NotificationSettingEntity;
import com.aistockadvisor.notification.infra.NotificationSettingRepository;
import com.aistockadvisor.notification.service.NotificationDedupPolicy.Decision;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.service.QuoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 15분 주기로 알림 설정된 종목을 체크하여 조건 충족 시 Push 전송.
 *
 * 중복 억제 정책:
 *   - 가격: 히스테리시스 + 쿨다운 ({@link NotificationDedupPolicy})
 *   - 뉴스: watermark({@code last_news_published_at}) 기반 ({@link NotificationNewsDedupPolicy})
 *   - 푸시 발송이 실제로 성공한 경우에만 상태를 저장 → 실패 시 다음 사이클 재시도 가능
 */
@Service
public class NotificationCheckService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCheckService.class);
    private static final int NEWS_FETCH_LIMIT = 5;
    private static final int BODY_MAX_LENGTH = 200;

    private final NotificationSettingRepository settingRepo;
    private final QuoteService quoteService;
    private final PushService pushService;
    private final NewsService newsService;
    private final NotificationDedupProperties dedupProperties;

    public NotificationCheckService(NotificationSettingRepository settingRepo,
                                    QuoteService quoteService,
                                    PushService pushService,
                                    NewsService newsService,
                                    NotificationDedupProperties dedupProperties) {
        this.settingRepo = settingRepo;
        this.quoteService = quoteService;
        this.pushService = pushService;
        this.newsService = newsService;
        this.dedupProperties = dedupProperties;
    }

    @Scheduled(fixedRate = 900_000) // 15분
    public void check() {
        if (!pushService.isEnabled()) return;

        List<NotificationSettingEntity> allEnabled = settingRepo.findAll().stream()
                .filter(NotificationSettingEntity::isEnabled)
                .toList();

        if (allEnabled.isEmpty()) return;

        Set<String> tickers = allEnabled.stream()
                .map(NotificationSettingEntity::getTicker)
                .collect(Collectors.toSet());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        for (String ticker : tickers) {
            try {
                Quote quote = quoteService.getQuote(ticker);
                List<NotificationSettingEntity> settings = allEnabled.stream()
                        .filter(s -> s.getTicker().equals(ticker))
                        .toList();

                for (NotificationSettingEntity setting : settings) {
                    if (quote != null && quote.changePercent() != null) {
                        checkPriceThreshold(setting, quote, now);
                    }
                    if (setting.isOnNewNews()) {
                        checkNewNews(setting);
                    }
                }
            } catch (Exception e) {
                log.debug("Notification check skipped for {}: {}", ticker, e.getMessage());
            }
        }
    }

    private void checkPriceThreshold(NotificationSettingEntity setting, Quote quote, OffsetDateTime now) {
        if (setting.getPriceChangeThreshold() == null) return;

        BigDecimal absChange = quote.changePercent().abs();
        Decision decision = NotificationDedupPolicy.decide(
                absChange,
                setting.getPriceChangeThreshold(),
                dedupProperties.resetRatio(),
                dedupProperties.cooldown(),
                setting.isLastTriggeredAbove(),
                setting.getLastNotifiedAt(),
                now
        );

        switch (decision.action()) {
            case SEND -> {
                String direction = quote.changePercent().signum() > 0 ? "+" : "";
                boolean sent = pushService.sendToUser(
                        setting.getUserId(),
                        quote.ticker() + " 가격 알림",
                        quote.ticker() + " " + direction + quote.changePercent()
                                + "% 변동 (임계값: ±" + setting.getPriceChangeThreshold() + "%)",
                        "/stocks/" + quote.ticker()
                );
                if (sent) {
                    setting.markNotified(now);
                    settingRepo.save(setting);
                } else {
                    log.debug("Push send returned false for {} {}; state not advanced",
                            setting.getUserId(), setting.getTicker());
                }
            }
            case RESET_ONLY -> {
                setting.resetTrigger();
                settingRepo.save(setting);
                log.debug("Dedup state reset for {} {}: {}",
                        setting.getUserId(), setting.getTicker(), decision.reason());
            }
            case SKIP_NO_TRANSITION, SKIP_COOLDOWN, NOOP -> {
                if (log.isDebugEnabled()) {
                    log.debug("Notification skipped for {} {}: {} ({})",
                            setting.getUserId(), setting.getTicker(),
                            decision.action(), decision.reason());
                }
            }
        }
    }

    private void checkNewNews(NotificationSettingEntity setting) {
        List<NewsItem> news;
        try {
            news = newsService.getNews(setting.getTicker(), NEWS_FETCH_LIMIT);
        } catch (Exception e) {
            log.debug("News fetch skipped for {}: {}", setting.getTicker(), e.getMessage());
            return;
        }

        NotificationNewsDedupPolicy.Decision decision =
                NotificationNewsDedupPolicy.decide(news, setting.getLastNewsPublishedAt());

        switch (decision.action()) {
            case BASELINE -> {
                setting.markNewsNotified(decision.watermark());
                settingRepo.save(setting);
                if (log.isDebugEnabled()) {
                    log.debug("News baseline set for {} {}: {}",
                            setting.getUserId(), setting.getTicker(), decision.watermark());
                }
            }
            case SEND -> {
                NewsItem target = decision.target();
                String title = target.ticker() + " 새 뉴스";
                String headline = target.titleKo() != null ? target.titleKo() : target.titleEn();
                String body = buildNewsBody(headline, decision.newerCount());
                String url = "/stocks/" + target.ticker();

                boolean sent = pushService.sendToUser(setting.getUserId(), title, body, url);
                if (sent) {
                    setting.markNewsNotified(decision.watermark());
                    settingRepo.save(setting);
                } else {
                    log.debug("News push returned false for {} {}; watermark not advanced",
                            setting.getUserId(), setting.getTicker());
                }
            }
            case NOOP -> {
                /* no-op */
            }
        }
    }

    private static String buildNewsBody(String headline, int newerCount) {
        String base = newerCount > 1
                ? headline + " 외 " + (newerCount - 1) + "건"
                : headline;
        return base.length() > BODY_MAX_LENGTH
                ? base.substring(0, BODY_MAX_LENGTH - 1) + "…"
                : base;
    }
}
