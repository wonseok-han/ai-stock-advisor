package com.aistockadvisor.notification.service;

import com.aistockadvisor.news.domain.NewsItem;
import com.aistockadvisor.notification.service.NotificationNewsDedupPolicy.Action;
import com.aistockadvisor.notification.service.NotificationNewsDedupPolicy.Decision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationNewsDedupPolicyTest {

    private static final Instant T0 = Instant.parse("2026-04-20T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-04-20T09:15:00Z");
    private static final Instant T2 = Instant.parse("2026-04-20T09:30:00Z");
    private static final Instant T3 = Instant.parse("2026-04-20T09:45:00Z");

    private NewsItem item(String hash, Instant publishedAt) {
        return new NewsItem(
                "AAPL", hash, "source", "https://example.com/" + hash,
                "Title EN " + hash, "제목 KO " + hash, "요약",
                NewsItem.Sentiment.NEUTRAL, publishedAt, Instant.now(), null
        );
    }

    @Test
    @DisplayName("N1: 뉴스 0건 → NOOP, watermark=null")
    void n1_emptyListReturnsNoop() {
        Decision d = NotificationNewsDedupPolicy.decide(List.of(), T0);

        assertThat(d.action()).isEqualTo(Action.NOOP);
        assertThat(d.target()).isNull();
        assertThat(d.newerCount()).isZero();
        assertThat(d.watermark()).isNull();
    }

    @Test
    @DisplayName("N2: watermark=null, 뉴스 3건 → BASELINE, watermark=최신 publishedAt")
    void n2_nullWatermarkYieldsBaseline() {
        List<NewsItem> news = List.of(item("a", T1), item("b", T3), item("c", T2));

        Decision d = NotificationNewsDedupPolicy.decide(news, null);

        assertThat(d.action()).isEqualTo(Action.BASELINE);
        assertThat(d.target()).isNull();
        assertThat(d.newerCount()).isZero();
        assertThat(d.watermark()).isEqualTo(T3);
    }

    @Test
    @DisplayName("N3: watermark=t0, 모두 publishedAt ≤ t0 → NOOP")
    void n3_allBelowWatermarkReturnsNoop() {
        List<NewsItem> news = List.of(item("a", T0), item("b", T0.minusSeconds(60)));

        Decision d = NotificationNewsDedupPolicy.decide(news, T0);

        assertThat(d.action()).isEqualTo(Action.NOOP);
        assertThat(d.target()).isNull();
        assertThat(d.newerCount()).isZero();
        assertThat(d.watermark()).isNull();
    }

    @Test
    @DisplayName("N4: watermark=t0, 1건만 publishedAt > t0 → SEND (newerCount=1)")
    void n4_singleNewerItemReturnsSend() {
        List<NewsItem> news = List.of(item("old", T0), item("new", T2));

        Decision d = NotificationNewsDedupPolicy.decide(news, T0);

        assertThat(d.action()).isEqualTo(Action.SEND);
        assertThat(d.target().articleUrlHash()).isEqualTo("new");
        assertThat(d.newerCount()).isEqualTo(1);
        assertThat(d.watermark()).isEqualTo(T2);
    }

    @Test
    @DisplayName("N5: watermark=t0, 3건 중 2건 newer → SEND, target=최신, newerCount=2")
    void n5_multipleNewerItemsReturnsSend() {
        List<NewsItem> news = List.of(
                item("oldest", T0),
                item("newer1", T1),
                item("newest", T3)
        );

        Decision d = NotificationNewsDedupPolicy.decide(news, T0);

        assertThat(d.action()).isEqualTo(Action.SEND);
        assertThat(d.target().articleUrlHash()).isEqualTo("newest");
        assertThat(d.newerCount()).isEqualTo(2);
        assertThat(d.watermark()).isEqualTo(T3);
    }

    @Test
    @DisplayName("N6: publishedAt null 혼재 → null 제외 후 정상 동작")
    void n6_nullPublishedAtFilteredOut() {
        List<NewsItem> news = List.of(
                item("nulltime", null),
                item("valid", T2)
        );

        // watermark=null 인 경우 valid 의 T2 가 baseline 으로 전진
        Decision baseline = NotificationNewsDedupPolicy.decide(news, null);
        assertThat(baseline.action()).isEqualTo(Action.BASELINE);
        assertThat(baseline.watermark()).isEqualTo(T2);

        // watermark=T0 인 경우 valid 한 T2 만 newer → SEND
        Decision send = NotificationNewsDedupPolicy.decide(news, T0);
        assertThat(send.action()).isEqualTo(Action.SEND);
        assertThat(send.target().articleUrlHash()).isEqualTo("valid");
        assertThat(send.newerCount()).isEqualTo(1);
        assertThat(send.watermark()).isEqualTo(T2);

        // null 뉴스만 있는 경우 NOOP
        Decision allNull = NotificationNewsDedupPolicy.decide(List.of(item("x", null)), T0);
        assertThat(allNull.action()).isEqualTo(Action.NOOP);
    }
}
