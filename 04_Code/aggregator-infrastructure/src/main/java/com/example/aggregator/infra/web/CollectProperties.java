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
}
