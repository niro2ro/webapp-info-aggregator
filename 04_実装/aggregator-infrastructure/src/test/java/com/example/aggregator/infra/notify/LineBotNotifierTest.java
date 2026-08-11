package com.example.aggregator.infra.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aggregator.domain.model.NotifyStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** LINE のHTTPステータス → NotifyStatus 分類（外部IF §3.4）を固定する。 */
class LineBotNotifierTest {

    @ParameterizedTest(name = "HTTP {0} → {1}")
    @CsvSource({
            "401, AUTH_FAILED",
            "403, BLOCKED",
            "429, RATE_LIMITED",
            "400, FORMAT_ERROR",
            "500, TEMP_ERROR",
            "503, TEMP_ERROR",
    })
    @DisplayName("設計の対応表どおりに分類される")
    void classifyKnownCodes(int code, NotifyStatus expected) {
        assertThat(LineBotNotifier.classifyHttpCode(code)).isEqualTo(expected);
    }

    @Test
    @DisplayName("表にない 4xx は不明(Blocked)扱い＝次回再送に回す")
    void unknownClientErrorIsBlocked() {
        assertThat(LineBotNotifier.classifyHttpCode(404)).isEqualTo(NotifyStatus.BLOCKED);
        assertThat(LineBotNotifier.classifyHttpCode(409)).isEqualTo(NotifyStatus.BLOCKED);
    }
}
