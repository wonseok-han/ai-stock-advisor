package com.aistockadvisor.notification.service;

import com.aistockadvisor.news.domain.NewsItem;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 뉴스 알림 중복 억제 정책. 순수 함수 — DI·Spring 의존 없음.
 *
 * 규칙:
 *   1. BASELINE: watermark 가 null 이면 최신 뉴스 publishedAt 으로 세팅만 (발송 X).
 *   2. SEND: watermark 보다 나중에 published 된 뉴스가 1건 이상이면 발송 후보.
 *   3. NOOP: 뉴스 없음 또는 모두 watermark 이하.
 *
 * Decision 에 포함되는 target 은 newer 중 최신 1건, newerCount 는 body 요약용 개수,
 * watermark 는 전진할 새 값 (BASELINE 또는 SEND 일 때 non-null).
 */
public final class NotificationNewsDedupPolicy {

    private NotificationNewsDedupPolicy() {}

    public enum Action { SEND, BASELINE, NOOP }

    public record Decision(Action action, NewsItem target, int newerCount, Instant watermark) {}

    public static Decision decide(List<NewsItem> news, Instant currentWatermark) {
        if (news == null || news.isEmpty()) {
            return new Decision(Action.NOOP, null, 0, null);
        }
        List<NewsItem> sorted = news.stream()
                .filter(n -> n.publishedAt() != null)
                .sorted(Comparator.comparing(NewsItem::publishedAt).reversed())
                .toList();
        if (sorted.isEmpty()) {
            return new Decision(Action.NOOP, null, 0, null);
        }
        Instant latest = sorted.get(0).publishedAt();

        if (currentWatermark == null) {
            return new Decision(Action.BASELINE, null, 0, latest);
        }

        List<NewsItem> newer = sorted.stream()
                .filter(n -> n.publishedAt().isAfter(currentWatermark))
                .toList();
        if (newer.isEmpty()) {
            return new Decision(Action.NOOP, null, 0, null);
        }
        return new Decision(Action.SEND, newer.get(0), newer.size(), latest);
    }
}
