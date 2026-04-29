package com.nowini.notification.domain;

import jakarta.validation.constraints.NotBlank;

public record PushUnsubscribeRequest(@NotBlank String endpoint) {
}
