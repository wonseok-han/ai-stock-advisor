package com.aistockadvisor.bookmark.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddBookmarkRequest(
        @NotBlank @Size(max = 16) String ticker
) {
}
