package com.nowini.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushServiceTest {

    @Test
    @DisplayName("P1: url 없는 3-arg 경로 (null 전달) → url 키 미포함")
    void p1_noUrl_omitsUrlKey() {
        String payload = PushService.buildPayload("AAPL 가격 알림", "변동 5%", null);

        assertThat(payload).contains("\"title\":\"AAPL 가격 알림\"");
        assertThat(payload).contains("\"body\":\"변동 5%\"");
        assertThat(payload).contains("\"icon\":\"/icon.svg\"");
        assertThat(payload).doesNotContain("\"url\"");
    }

    @Test
    @DisplayName("P2: url=\"/stocks/AAPL\" 전달 → url 키 포함")
    void p2_withUrl_includesUrlKey() {
        String payload = PushService.buildPayload("AAPL 새 뉴스", "제목", "/stocks/AAPL");

        assertThat(payload).contains("\"title\":\"AAPL 새 뉴스\"");
        assertThat(payload).contains("\"body\":\"제목\"");
        assertThat(payload).contains("\"icon\":\"/icon.svg\"");
        assertThat(payload).contains("\"url\":\"/stocks/AAPL\"");
    }

    @Test
    @DisplayName("P3: url=null → P1 과 동일하게 url 키 미포함")
    void p3_explicitNullUrl_sameAsNoUrl() {
        String withoutUrl = PushService.buildPayload("t", "b", null);
        assertThat(withoutUrl).doesNotContain("\"url\"");
    }

    @Test
    @DisplayName("P4: url=\"\" (빈 문자열) → url 키 미포함")
    void p4_blankUrl_omitsUrlKey() {
        String blank = PushService.buildPayload("t", "b", "");
        String whitespace = PushService.buildPayload("t", "b", "   ");

        assertThat(blank).doesNotContain("\"url\"");
        assertThat(whitespace).doesNotContain("\"url\"");
    }

    @Test
    @DisplayName("P5: title/body/url 의 큰따옴표는 이스케이프된다")
    void p5_escapesDoubleQuotes() {
        String payload = PushService.buildPayload(
                "say \"hi\"", "body \"x\"", "/path/\"q\"");

        assertThat(payload).contains("\\\"hi\\\"");
        assertThat(payload).contains("body \\\"x\\\"");
        assertThat(payload).contains("/path/\\\"q\\\"");
    }
}
