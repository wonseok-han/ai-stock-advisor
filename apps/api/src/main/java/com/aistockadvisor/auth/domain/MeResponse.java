package com.aistockadvisor.auth.domain;

import java.util.UUID;

public record MeResponse(UUID id, String email) {
}
