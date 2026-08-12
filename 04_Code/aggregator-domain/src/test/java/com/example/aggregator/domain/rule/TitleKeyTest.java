package com.example.aggregator.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 重複判定用タイトルキー（同一タイトルの集約）を検証する。 */
class TitleKeyTest {

    @Test
    @DisplayName("末尾の「 - 媒体名」を除去（別媒体でも同じキーになる）")
    void stripsPublisherSuffix() {
        String a = TitleKey.of("呪術廻戦 新作フィギュア予約開始 - Yahoo!ニュース");
        String b = TitleKey.of("呪術廻戦 新作フィギュア予約開始 - dメニューニュース");
        assertThat(a).isEqualTo("呪術廻戦 新作フィギュア予約開始");
        assertThat(a).isEqualTo(b);   // 別サイトでも同一キー
    }

    @Test
    @DisplayName("媒体名が付かないタイトルはそのまま（前後trim・空白畳み）")
    void plainTitle() {
        assertThat(TitleKey.of("  新作　フィギュア  登場 ")).isEqualTo("新作 フィギュア 登場");
    }

    @Test
    @DisplayName("null/空は空キー（＝集約対象外）")
    void nullSafe() {
        assertThat(TitleKey.of(null)).isEmpty();
        assertThat(TitleKey.of("   ")).isEmpty();
    }
}
