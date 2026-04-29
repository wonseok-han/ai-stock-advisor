package com.nowini.ai.service;

import com.nowini.ai.domain.EvaluationWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * N일 정합도 평가 일일 배치.
 *
 * <p>{@code test} 프로파일에선 비활성 — 통합 테스트와 충돌 방지.
 *
 * 참조: docs/02-design/features/signal-accuracy.design.md §11.2 Step 4
 */
@Component
@Profile("!test")
public class SignalEvaluationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SignalEvaluationScheduler.class);

    private final SignalEvaluationService service;

    public SignalEvaluationScheduler(SignalEvaluationService service) {
        this.service = service;
    }

    /**
     * UTC 23:00 (= NY ET 19:00, 장 마감 3시간 후) 실행.
     * 각 window 별 미평가 audit 을 일괄 처리.
     */
    @Scheduled(cron = "${app.ai.evaluation.cron:0 0 23 * * *}", zone = "UTC")
    public void evaluateAll() {
        for (EvaluationWindow w : EvaluationWindow.DEFAULTS) {
            try {
                SignalEvaluationService.EvaluationStats stats = service.evaluateWindow(w, null);
                log.info("signal-evaluation scheduler window={} stats={}", w.days(), stats);
            } catch (Exception ex) {
                log.error("signal-evaluation scheduler failed window={} reason={}",
                        w.days(), ex.getMessage(), ex);
            }
        }
    }
}
