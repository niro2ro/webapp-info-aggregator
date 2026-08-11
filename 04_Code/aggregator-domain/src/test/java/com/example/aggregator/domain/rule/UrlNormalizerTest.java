package com.example.aggregator.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** URL 正規化（FR-02-07）。表記ゆれを吸収して同一記事を同じキーへ落とすことを確認する。 */
class UrlNormalizerTest {

    private final UrlNormalizer n = new UrlNormalizer();

    @Test
    @DisplayName("クエリとフラグメントを除去する")
    void stripsQueryAndFragment() {
        assertThat(n.normalize("https://example.com/a/b?utm=x&y=1#top"))
                .isEqualTo("https://example.com/a/b");
    }

    @Test
    @DisplayName("スキーム・ホストを小文字化する")
    void lowercasesSchemeAndHost() {
        assertThat(n.normalize("HTTPS://Example.COM/Path"))
                .isEqualTo("https://example.com/Path");   // パスの大文字は保持
    }

    @Test
    @DisplayName("既定ポートは除去し、非既定ポートは残す")
    void handlesPorts() {
        assertThat(n.normalize("https://example.com:443/a")).isEqualTo("https://example.com/a");
        assertThat(n.normalize("http://example.com:80/a")).isEqualTo("http://example.com/a");
        assertThat(n.normalize("https://example.com:8443/a")).isEqualTo("https://example.com:8443/a");
    }

    @Test
    @DisplayName("末尾スラッシュを除去する（ルート単独のスラッシュは残す）")
    void trimsTrailingSlash() {
        assertThat(n.normalize("https://example.com/a/")).isEqualTo("https://example.com/a");
    }

    @Test
    @DisplayName("表記ゆれのある2つのURLが同じ正規形になる")
    void twoVariantsConverge() {
        String a = n.normalize("https://Example.com/news/123/?ref=rss#c");
        String b = n.normalize("https://example.com/news/123");
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("null はそのまま null")
    void nullIsNull() {
        assertThat(n.normalize(null)).isNull();
    }
}
