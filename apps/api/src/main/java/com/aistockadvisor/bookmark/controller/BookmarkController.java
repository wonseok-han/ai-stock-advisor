package com.aistockadvisor.bookmark.controller;

import com.aistockadvisor.bookmark.domain.AddBookmarkRequest;
import com.aistockadvisor.bookmark.domain.BookmarkCheckResponse;
import com.aistockadvisor.bookmark.domain.BookmarkListResponse;
import com.aistockadvisor.bookmark.domain.BookmarkResponse;
import com.aistockadvisor.bookmark.service.BookmarkService;
import com.aistockadvisor.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookmarks")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookmarkResponse add(@Valid @RequestBody AddBookmarkRequest request, Principal principal) {
        UUID userId = AuthenticatedUser.userId(principal);
        return bookmarkService.add(userId, request.ticker());
    }

    @DeleteMapping("/{ticker}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String ticker, Principal principal) {
        UUID userId = AuthenticatedUser.userId(principal);
        bookmarkService.remove(userId, ticker);
    }

    @GetMapping
    public BookmarkListResponse list(Principal principal) {
        UUID userId = AuthenticatedUser.userId(principal);
        return bookmarkService.list(userId);
    }

    @GetMapping("/check/{ticker}")
    public BookmarkCheckResponse check(@PathVariable String ticker, Principal principal) {
        UUID userId = AuthenticatedUser.userId(principal);
        return new BookmarkCheckResponse(bookmarkService.check(userId, ticker));
    }
}
