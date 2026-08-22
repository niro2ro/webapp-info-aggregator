package com.example.aggregator.infra.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aggregator.domain.collect.RawItem;
import com.example.aggregator.infra.rss.RomeFeedFetcher;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 収集の「HTTP取得→RSS解析」を<b>実際のHTTP通信</b>で検証するループバック結合テスト。
 *
 * <p>本物の {@link JdkHttpFetcher}（JDK HttpClient・UA付与・リトライ・4xx分類）と本物の
 * {@link RomeFeedFetcher}（ROME）を、ローカルに立てた {@link HttpServer} が返す RSS に対して通す。
 * 外部ネットワークに出ずに、収集バッチが実際に使う取得・解析経路が「実ソケット越しに動く」ことを実証する。
 * ＝実サイトのRSS URL を差し替えれば同じ経路で取得できる、という確証になる（実サイト到達性のみ環境依存）。
 */
class HttpFetchAndParseLoopbackTest {

    private HttpServer server;
    private String baseUrl;
    /** サーバが実際に受け取った User-Agent を記録し、収集マナー（連絡先入りUA）を検証する。 */
    private final AtomicReference<String> seenUserAgent = new AtomicReference<>();

    private static final String RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
              <title>テストフィード</title>
              <link>http://example.test/</link>
              <description>d</description>
              <item>
                <title>夏目友人帳 一番くじ 発売決定</title>
                <link>http://example.test/articles/1</link>
                <description>グッズ情報の説明文</description>
                <category>グッズ</category>
                <pubDate>Wed, 12 Aug 2026 01:00:00 GMT</pubDate>
              </item>
              <item>
                <title>アニメ 第2期 放送日発表</title>
                <link>http://example.test/articles/2</link>
                <pubDate>Thu, 13 Aug 2026 02:30:00 GMT</pubDate>
              </item>
            </channel></rss>
            """;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); // 0=空きポート自動
        server.createContext("/rss", ex -> {
            seenUserAgent.set(ex.getRequestHeaders().getFirst("User-Agent"));
            byte[] body = RSS.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/rss+xml; charset=UTF-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });
        server.createContext("/missing", ex -> {   // 4xx（非リトライ）確認用
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    /** テスト用に間隔待機を0にした本物の取得器（同一ホスト間隔は本番の関心事だがテストは高速化）。 */
    private JdkHttpFetcher fetcher() {
        CollectProperties props = new CollectProperties();
        props.setMinHostIntervalMs(0);
        props.setConnectTimeoutMs(2000);
        props.setReadTimeoutMs(3000);
        return new JdkHttpFetcher(props, "Aggregator-Test/0.1 (contact: test@example.com)");
    }

    @Test
    @DisplayName("実HTTPでRSSを取得→ROMEで解析し、記事が構造化される（取得・解析経路が実ソケットで動く）")
    void fetchThenParse_realHttp() {
        String xml = fetcher().get(baseUrl + "/rss");            // 実HTTP GET
        List<RawItem> items = new RomeFeedFetcher().parse(xml);  // 実ROME解析

        assertThat(items).hasSize(2);
        RawItem first = items.get(0);
        assertThat(first.title()).isEqualTo("夏目友人帳 一番くじ 発売決定");
        assertThat(first.url()).isEqualTo("http://example.test/articles/1");
        assertThat(first.publishedAt()).isNotNull();             // pubDate が Instant に解釈される
        assertThat(first.categoryHint()).isEqualTo("グッズ");
        assertThat(items.get(1).title()).isEqualTo("アニメ 第2期 放送日発表");
    }

    @Test
    @DisplayName("取得時に連絡先入り User-Agent が実際に送信される（収集マナー）")
    void sendsContactUserAgent() {
        fetcher().get(baseUrl + "/rss");
        assertThat(seenUserAgent.get()).contains("Aggregator-Test").contains("contact:");
    }

    @Test
    @DisplayName("404 は 4xx として非リトライで HttpStatusException を投げる（robotsゲートが分類に使う）")
    void notFoundThrowsClientError() {
        assertThatThrownBy(() -> fetcher().get(baseUrl + "/missing"))
                .isInstanceOf(HttpStatusException.class)
                .satisfies(e -> assertThat(((HttpStatusException) e).getStatus()).isEqualTo(404));
    }
}
