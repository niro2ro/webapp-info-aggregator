package com.example.aggregator.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** URL ハッシュ（FR-02-07/08）。冪等の一次キー url_hash に使う SHA-256 16進文字列を検証する。 */
class UrlHasherTest {

    private final UrlHasher h = new UrlHasher();

    @Test
    @DisplayName("SHA-256 の既知ベクタと一致（64桁の16進）")
    void knownVector() {
        // 空文字列の SHA-256 は既知値。決定性の担保。
        assertThat(h.hash(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("同じ入力は必ず同じハッシュ（決定的）")
    void deterministic() {
        String url = "https://example.com/news/123";
        assertThat(h.hash(url)).isEqualTo(h.hash(url));
    }

    @Test
    @DisplayName("異なる入力は異なるハッシュ、長さは常に64桁")
    void differsAndFixedLength() {
        String a = h.hash("https://example.com/a");
        String b = h.hash("https://example.com/b");
        assertThat(a).isNotEqualTo(b);
        assertThat(a).hasSize(64).matches("[0-9a-f]{64}");
    }
}
