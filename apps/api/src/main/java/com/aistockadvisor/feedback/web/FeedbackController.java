package com.aistockadvisor.feedback.web;

import com.aistockadvisor.feedback.domain.FeedbackRequest;
import com.aistockadvisor.feedback.domain.FeedbackResponse;
import com.aistockadvisor.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse submit(@Valid @RequestBody FeedbackRequest request) {
        UUID userId = null;
        if (request.userId() != null && !request.userId().isBlank()) {
            try {
                userId = UUID.fromString(request.userId());
            } catch (IllegalArgumentException ignored) {}
        }
        return feedbackService.submit(request, userId);
    }
}
