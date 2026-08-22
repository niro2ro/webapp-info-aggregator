package com.example.aggregator.infra.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 収集の HTTP マナー・信頼性の設定（app.collect.*）。タイムアウト・リトライ回数・間隔を<b>外部化</b>する
 * （BD-BATCH-00-07）。User-Agent は {@code app.user-agent}（連絡先入り）を流用する。
 */
@Component
@ConfigurationProperties(prefix = "app.collect")
public class CollectProperties {

    /** 接続タイムアウト（ミリ秒）。 */
    private int connectTimeoutMs = 5000;
    /** 読み取りタイムアウト（ミリ秒）。 */
    private int readTimeoutMs = 10000;
    /** 最大試行回数（初回＋リトライ）。一時障害のみ指数バックオフで再試行。 */
    private int maxAttempts = 3;
    /** バックオフ初期値（ミリ秒）。以降 2 倍で増やす。 */
    private long backoffMs = 1000;
    /** 同一ホストへの最小アクセス間隔（ミリ秒・BD-IF-01-02）。 */
    private long minHostIntervalMs = 1000;

    /**
     * テーマ検索収集で使う「キーワード検索RSS」の URL テンプレート（{q} をURLエンコード済みキーワードに置換）。
     * 既定は Googleニュースの検索フィード（日本語）。<b>利用規約は各自で確認</b>（別サービスに差し替え可能）。
     */
    private String searchFeedUrlTemplate =
            "https://news.google.com/rss/search?q={q}&hl=ja&gl=JP&ceid=JP:ja";

    /** LLM へ渡す記事本文テキストの最大文字数（トークン/コストを抑えるため切り詰め）。 */
    private int maxBodyChars = 4000;

    /**
     * テーマ検索収集で 1テーマあたり処理する記事の上限。広いキーワード（例「ポケモン」）だと検索RSSが
     * 大量に返り、1件ごとの本文取得＋LLM補完が直列で走って収集が長時間化する。上限で切り、実行時間を読める
     * ようにする（新着はどのみち上位に来るため上位Nで十分）。0以下は無制限。
     */
    private int searchMaxItemsPerTheme = 30;

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int v) { this.connectTimeoutMs = v; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int v) { this.readTimeoutMs = v; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int v) { this.maxAttempts = v; }
    public long getBackoffMs() { return backoffMs; }
    public void setBackoffMs(long v) { this.backoffMs = v; }
    public long getMinHostIntervalMs() { return minHostIntervalMs; }
    public void setMinHostIntervalMs(long v) { this.minHostIntervalMs = v; }
    public String getSearchFeedUrlTemplate() { return searchFeedUrlTemplate; }
    public void setSearchFeedUrlTemplate(String v) { this.searchFeedUrlTemplate = v; }
    public int getMaxBodyChars() { return maxBodyChars; }
    public void setMaxBodyChars(int v) { this.maxBodyChars = v; }
    public int getSearchMaxItemsPerTheme() { return searchMaxItemsPerTheme; }
    public void setSearchMaxItemsPerTheme(int v) { this.searchMaxItemsPerTheme = v; }
}
