package com.aistockadvisor.ai.web;

import com.aistockadvisor.ai.domain.AccuracySummary;
import com.aistockadvisor.ai.domain.EvaluationWindow;
import com.aistockadvisor.ai.service.SignalAccuracyService;
import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전역 AI 시그널 정합도 API.
 *
 * <p>FE 가 종목 상세 페이지에서 조회하여 정보성 배지를 렌더한다.
 * 종목별 엔드포인트는 의도적으로 제공하지 않음 (유사투자자문 해석 여지 차단).
 *
 * 참조: docs/02-design/features/signal-accuracy.design.md §4.2
 */
@RestController
@RequestMapping("/api/v1/ai/accuracy")
public class SignalAccuracyController {

    private final SignalAccuracyService service;

    public SignalAccuracyController(SignalAccuracyService service) {
        this.service = service;
    }

    @GetMapping
    public AccuracySummary get(@RequestParam(value = "window", defaultValue = "30") int window) {
        if (!EvaluationWindow.ALLOWED.contains(window)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "window must be one of " + EvaluationWindow.ALLOWED);
        }
        return service.summarize(new EvaluationWindow(window));
    }
}
