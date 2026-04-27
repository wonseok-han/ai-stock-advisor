package com.aistockadvisor.feedback.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
        String userId,
        @NotBlank @Pattern(regexp = "bug|question|suggestion") String type,
        @NotBlank @Size(min = 1, max = 200) String subject,
        @NotBlank @Size(min = 10, max = 2000) String body,
        @Email String email,
        String url,
        String userAgent
) {}
