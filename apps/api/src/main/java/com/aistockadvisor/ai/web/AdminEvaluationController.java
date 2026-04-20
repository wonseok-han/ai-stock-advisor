package com.aistockadvisor.ai.web;

import com.aistockadvisor.ai.domain.EvaluationWindow;
import com.aistockadvisor.ai.service.SignalEvaluationService;
import com.aistockadvisor.common.error.BusinessException;
import com.aistockadvisor.common.error.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자용 시그널 정합도 평가 백필 API.
 *
 * <p>Basic Auth (admin 체인) 으로 보호. 요청은 즉시 수락되고 실제 평가는
 * virtual-thread 에서 비동기로 수행된다.
 *
 * 참조: docs/02-design/features/signal-accuracy.design.md §4.2
 */
@RestController
@RequestMapping("/api/admin/ai")
public class AdminEvaluationController {

    private final SignalEvaluationService service;

    public AdminEvaluationController(SignalEvaluationService service) {
        this.service = service;
    }

    @PostMapping("/backfill-evaluation")
    public ResponseEntity<BackfillEvaluationResponse> backfill(
            @Valid @RequestBody BackfillEvaluationRequest req) {
        if (req.window() == null || !EvaluationWindow.ALLOWED.contains(req.window())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "window must be one of " + EvaluationWindow.ALLOWED);
        }

        EvaluationWindow window = new EvaluationWindow(req.window());
        long candidateCount = service.countUnevaluated(window, req.since());

        service.evaluateWindowAsync(window, req.since());

        return ResponseEntity.accepted().body(new BackfillEvaluationResponse(
                true, candidateCount, service.batchSize()));
    }
}
