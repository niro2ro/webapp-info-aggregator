package com.example.aggregator.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.aggregator.domain.collect.HttpFetcher;
import com.example.aggregator.domain.model.FetchType;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.infra.rss.RomeFeedFetcher;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RSS取得診断: 取得（モック）＋解析（本物のROME）で、成功件数・サンプル上限・失敗分類（取得/解析/URL不正）を検証する。
 * 解析は実物の {@link RomeFeedFetcher} を使い「本当にRSSとして読めるか」を確認する（ネットワークはモックで遮断）。
 */
@ExtendWith(MockitoExtension.class)
class FeedProbeServiceTest {

    @Mock HttpFetcher http;

    private FeedProbeService service() {
        return new FeedProbeService(http, new RomeFeedFetcher());
    }

    /** item を n 件持つ最小の RSS 2.0 文書。 */
    private static String rssWith(int n) {
        StringBuilder sb = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss version=\"2.0\"><channel><title>t</title>"
                + "<link>http://example.com</link><description>d</description>");
        for (int i = 1; i <= n; i++) {
            sb.append("<item><title>記事").append(i).append("</title>")
              .append("<link>http://example.com/").append(i).append("</link>")
              .append("<pubDate>Wed, 12 Aug 2026 01:00:00 GMT</pubDate></item>");
        }
        return sb.append("</channel></rss>").toString();
    }

    @Test
    @DisplayName("正常なRSSは ok=true・全件数を返し、サンプルは上限件数に丸める")
    void success() {
        when(http.get(anyString())).thenReturn(rssWith(8));

        FeedProbeService.ProbeResult r = service().probe("https://example.com/rss.xml");

        assertThat(r.ok()).isTrue();
        assertThat(r.count()).isEqualTo(8);                                  // 総数は8
        assertThat(r.samples()).hasSize(FeedProbeService.SAMPLE_LIMIT);      // 表示は先頭5件
        assertThat(r.samples().get(0).title()).isEqualTo("記事1");
        assertThat(r.samples().get(0).publishedAt()).isNotNull();
    }

    @Test
    @DisplayName("フィードは読めたが記事0件なら ok=true・count=0")
    void okButEmpty() {
        when(http.get(anyString())).thenReturn(rssWith(0));

        FeedProbeService.ProbeResult r = service().probe("https://example.com/rss.xml");

        assertThat(r.ok()).isTrue();
        assertThat(r.count()).isZero();
        assertThat(r.samples()).isEmpty();
    }

    @Test
    @DisplayName("空URLは取得せず失敗を返す")
    void emptyUrl() {
        FeedProbeService.ProbeResult r = service().probe("   ");
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).contains("URL");
    }

    @Test
    @DisplayName("http/https以外のURLは失敗を返す")
    void nonHttpUrl() {
        FeedProbeService.ProbeResult r = service().probe("ftp://example.com/x");
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).contains("http");
    }

    @Test
    @DisplayName("取得で例外が出たら『取得に失敗』分類")
    void fetchFails() {
        when(http.get(anyString())).thenThrow(new RuntimeException("connect timed out"));

        FeedProbeService.ProbeResult r = service().probe("https://example.com/rss.xml");

        assertThat(r.ok()).isFalse();
        assertThat(r.error()).contains("取得に失敗");
    }

    @Test
    @DisplayName("RSSでない内容（HTML等）は『解析できません』分類")
    void notAFeed() {
        when(http.get(anyString())).thenReturn("<html><body>これはRSSではない</body></html>");

        FeedProbeService.ProbeResult r = service().probe("https://example.com/index.html");

        assertThat(r.ok()).isFalse();
        assertThat(r.error()).contains("解析");
    }

    @Test
    @DisplayName("情報源マスタの各URLを個別にテストし、1件失敗しても残りは続行する")
    void probeSourcesPerSource() {
        SourceEntity good = new SourceEntity("良RSS", "https://good.example.com/rss", FetchType.RSS);
        SourceEntity bad = new SourceEntity("不通", "https://bad.example.com/rss", FetchType.RSS);
        when(http.get("https://good.example.com/rss")).thenReturn(rssWith(2));
        when(http.get("https://bad.example.com/rss")).thenThrow(new RuntimeException("connect refused"));

        List<FeedProbeService.NamedProbe> results = service().probeSources(List.of(good, bad));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).name()).isEqualTo("良RSS");
        assertThat(results.get(0).result().ok()).isTrue();
        assertThat(results.get(0).result().count()).isEqualTo(2);
        assertThat(results.get(1).name()).isEqualTo("不通");
        assertThat(results.get(1).result().ok()).isFalse();       // 失敗しても結果に含まれる
        assertThat(results.get(1).url()).isEqualTo("https://bad.example.com/rss");
    }
}
