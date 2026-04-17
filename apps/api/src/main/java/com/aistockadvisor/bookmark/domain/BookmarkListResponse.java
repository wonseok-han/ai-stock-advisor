package com.aistockadvisor.bookmark.domain;

import java.util.List;

public record BookmarkListResponse(
        List<BookmarkResponse> bookmarks,
        int total
) {
}
