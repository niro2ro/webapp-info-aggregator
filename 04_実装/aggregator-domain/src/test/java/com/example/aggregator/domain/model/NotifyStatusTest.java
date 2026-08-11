package com.example.aggregator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NotifyStatus の分類セマンティクス（外部IF §3.4・例外/リトライ §4）を固定する。 */
class NotifyStatusTest {

    @Test
    @DisplayName("コード値が設計と一致する（Blocked は一意化のため 4）")
    void codes() {
        assertThat(NotifyStatus.SUCCESS.code()).isEqualTo((short) 0);
        assertThat(NotifyStatus.TEMP_ERROR.code()).isEqualTo((short) 1);
        assertThat(NotifyStatus.RATE_LIMITED.code()).isEqualTo((short) 2);
        assertThat(NotifyStatus.AUTH_FAILED.code()).isEqualTo((short) 3);
        assertThat(NotifyStatus.BLOCKED.code()).isEqualTo((short) 4);
        assertThat(NotifyStatus.FORMAT_ERROR.code()).isEqualTo((short) 5);
        assertThat(NotifyStatus.TIMEOUT.code()).isEqualTo((short) 6);
        assertThat(NotifyStatus.GAVE_UP.code()).isEqualTo((short) 9);
    }

    @Test
    @DisplayName("自動リトライは一時障害・レート制限のみ")
    void retryable() {
        assertThat(NotifyStatus.TEMP_ERROR.retryable()).isTrue();
        assertThat(NotifyStatus.RATE_LIMITED.retryable()).isTrue();
        assertThat(NotifyStatus.AUTH_FAILED.retryable()).isFalse();
        assertThat(NotifyStatus.FORMAT_ERROR.retryable()).isFalse();
        assertThat(NotifyStatus.TIMEOUT.retryable()).isFalse();
    }

    @Test
    @DisplayName("打ち切り（GaveUp化）は FormatError と GaveUp のみ")
    void giveUp() {
        assertThat(NotifyStatus.FORMAT_ERROR.giveUp()).isTrue();
        assertThat(NotifyStatus.GAVE_UP.giveUp()).isTrue();
        assertThat(NotifyStatus.AUTH_FAILED.giveUp()).isFalse();
        assertThat(NotifyStatus.TEMP_ERROR.giveUp()).isFalse();
    }

    @Test
    @DisplayName("fromCode は往復で一致する")
    void roundTrip() {
        for (NotifyStatus v : NotifyStatus.values()) {
            assertThat(NotifyStatus.fromCode(v.code())).isEqualTo(v);
        }
    }
}
