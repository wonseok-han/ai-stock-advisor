package com.aistockadvisor.notification.service;

import com.aistockadvisor.notification.infra.NotificationSettingEntity;
import com.aistockadvisor.notification.infra.NotificationSettingRepository;
import com.aistockadvisor.stock.domain.Quote;
import com.aistockadvisor.stock.service.QuoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 15분 주기로 알림 설정된 종목을 체크하여 조건 충족 시 Push 전송.
 * 체크 대상: enabled=true인 notification_settings의 고유 ticker 목록.
 */
@Service
public class NotificationCheckService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCheckService.class);

    private final NotificationSettingRepository settingRepo;
    private final QuoteService quoteService;
    private final PushService pushService;

    public NotificationCheckService(NotificationSettingRepository settingRepo,
                                    QuoteService quoteService,
                                    PushService pushService) {
        this.settingRepo = settingRepo;
        this.quoteService = quoteService;
        this.pushService = pushService;
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

        for (String ticker : tickers) {
            try {
                Quote quote = quoteService.getQuote(ticker);
                if (quote == null || quote.changePercent() == null) continue;

                List<NotificationSettingEntity> settings = allEnabled.stream()
                        .filter(s -> s.getTicker().equals(ticker))
                        .toList();

                for (NotificationSettingEntity setting : settings) {
                    checkPriceThreshold(setting, quote);
                }
            } catch (Exception e) {
                log.debug("Notification check skipped for {}: {}", ticker, e.getMessage());
            }
        }
    }

    private void checkPriceThreshold(NotificationSettingEntity setting, Quote quote) {
        if (setting.getPriceChangeThreshold() == null) return;
        BigDecimal absChange = quote.changePercent().abs();
        if (absChange.compareTo(setting.getPriceChangeThreshold()) >= 0) {
            String direction = quote.changePercent().signum() > 0 ? "+" : "";
            pushService.sendToUser(
                    setting.getUserId(),
                    quote.ticker() + " 가격 알림",
                    quote.ticker() + " " + direction + quote.changePercent() + "% 변동 (임계값: ±" + setting.getPriceChangeThreshold() + "%)"
            );
        }
    }
}
