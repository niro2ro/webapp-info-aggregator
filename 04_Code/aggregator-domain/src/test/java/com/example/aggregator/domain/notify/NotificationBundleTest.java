package com.example.aggregator.domain.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 冪等キー（retryKey）の決定性を固定する（BD-IF-03-04/05・二重配信防止）。 */
class NotificationBundleTest {

    private NotificationItem item(long id) {
        return new NotificationItem(id, "タイトル" + id, "https://example.com/" + id, "要約", LocalDate.of(2026, 8, 1));
    }

    @Test
    @DisplayName("同じ利用者×同じ記事集合なら（順序が違っても）retryKey は同一")
    void deterministicRegardlessOfOrder() {
        NotificationBundle a = NotificationBundle.of(2L, "U123", List.of(item(10), item(20), item(30)));
        NotificationBundle b = NotificationBundle.of(2L, "U123", List.of(item(30), item(10), item(20)));
        assertThat(a.retryKey()).isEqualTo(b.retryKey());
    }

    @Test
    @DisplayName("記事集合が違えば retryKey は変わる")
    void differsWhenSetDiffers() {
        NotificationBundle a = NotificationBundle.of(2L, "U123", List.of(item(10), item(20)));
        NotificationBundle b = NotificationBundle.of(2L, "U123", List.of(item(10), item(21)));
        assertThat(a.retryKey()).isNotEqualTo(b.retryKey());
    }

    @Test
    @DisplayName("利用者が違えば retryKey は変わる")
    void differsWhenUserDiffers() {
        NotificationBundle a = NotificationBundle.of(2L, "U123", List.of(item(10)));
        NotificationBundle b = NotificationBundle.of(3L, "U123", List.of(item(10)));
        assertThat(a.retryKey()).isNotEqualTo(b.retryKey());
    }
}
