package com.aistockadvisor.feedback.service;

import com.aistockadvisor.feedback.domain.FeedbackRequest;
import com.aistockadvisor.feedback.domain.FeedbackResponse;
import com.aistockadvisor.feedback.infra.FeedbackEntity;
import com.aistockadvisor.feedback.infra.FeedbackRepository;
import com.aistockadvisor.feedback.infra.ResendClient;
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
