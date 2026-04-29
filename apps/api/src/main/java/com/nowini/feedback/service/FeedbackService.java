package com.nowini.feedback.service;

import com.nowini.feedback.domain.FeedbackRequest;
import com.nowini.feedback.domain.FeedbackResponse;
import com.nowini.feedback.infra.FeedbackEntity;
import com.nowini.feedback.infra.FeedbackRepository;
import com.nowini.feedback.infra.ResendClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final ResendClient resendClient;

    public FeedbackService(FeedbackRepository feedbackRepository, ResendClient resendClient) {
        this.feedbackRepository = feedbackRepository;
        this.resendClient = resendClient;
    }

    public FeedbackResponse submit(FeedbackRequest request, UUID userId) {
        FeedbackEntity entity = new FeedbackEntity(
                userId,
                request.email(),
                request.type(),
                request.subject(),
                request.body(),
                request.url(),
                request.userAgent()
        );

        FeedbackEntity saved = feedbackRepository.save(entity);

        resendClient.sendFeedbackNotification(
                request.type(),
                request.subject(),
                request.body(),
                request.email()
        );

        return new FeedbackResponse(saved.getId(), saved.getStatus());
    }
}
