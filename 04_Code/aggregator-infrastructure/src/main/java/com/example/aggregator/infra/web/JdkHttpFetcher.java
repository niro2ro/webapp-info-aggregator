package com.example.aggregator.infra.web;

import com.example.aggregator.domain.collect.HttpFetcher;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HTTP 取得の実装（DD-CLS-14）。JDK 標準の {@link HttpClient} を用い、収集マナー・信頼性を内包する:
 *
 * <ul>
 *   <li><b>User-Agent に連絡先</b>（{@code app.user-agent}）を付与（BD-IF-01-03）</li>
 *   <li><b>接続/読み取りタイムアウト</b>（TimeLimiter 相当）</li>
 *   <li><b>一時障害（IOException/5xx/429）に指数バックオフでリトライ</b>（Resilience4j Retry）。4xx は非リトライ</li>
 *   <li><b>同一ホストへ最小1秒間隔</b>（BD-IF-01-02）。ホストごとの最終アクセス時刻で待機</li>
 * </ul>
 *
 * <p>設計反映メモ: 外部IFでは「RestClient＋Resilience4j」を挙げているが、収集ライブラリに Web スタックを
 * 持ち込まないため HTTP は JDK {@code HttpClient} を採用（タイムアウト/ヘッダ/本文取得の要件は同等）。
 * リトライは設計どおり Resilience4j を使用。サーキットブレーカーは情報源数が少ないため将来追加とする。
 */
@Component
public class JdkHttpFetcher implements HttpFetcher {

    private static final Logger log = LoggerFactory.getLogger(JdkHttpFetcher.class);

    private final HttpClient client;
    private final CollectProperties props;
    private final String userAgent;
    private final Retry retry;
    private final ConcurrentHashMap<String, Long> lastAccessByHost = new ConcurrentHashMap<>();

    public JdkHttpFetcher(CollectProperties props, @Value("${app.user-agent}") String userAgent) {
        this.props = props;
        this.userAgent = userAgent;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)   // リダイレクト解決（正規化前の前処理）
                .build();
        RetryConfig cfg = RetryConfig.custom()
                .maxAttempts(props.getMaxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(props.getBackoffMs(), 2.0))
                .retryOnException(e -> e instanceof TransientHttpException)   // 一時障害のみ再試行
                .build();
        this.retry = Retry.of("httpFetch", cfg);
    }

    @Override
    public String get(String url) {
        Supplier<String> decorated = Retry.decorateSupplier(retry, () -> doGet(url));
        return decorated.get();   // maxAttempts 使い切ったら最後の例外を投げる
    }

    private String doGet(String url) {
        respectHostInterval(url);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .timeout(Duration.ofMillis(props.getReadTimeoutMs()))
                .GET()
                .build();
        try {
            HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
            int s = res.statusCode();
            if (s >= 200 && s < 300) return res.body();
            if (s >= 500 || s == 429) throw new TransientHttpException("HTTP " + s + " (一時): " + url);
            throw new HttpStatusException(s, "HTTP " + s + ": " + url);   // 4xx は非リトライ
        } catch (java.io.IOException e) {
            throw new TransientHttpException("接続/読取エラー: " + url + " (" + e.getMessage() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientHttpException("中断: " + url);
        }
    }

    /** 同一ホストへは最小間隔を空ける（前回アクセスからの経過が不足なら待つ）。 */
    private void respectHostInterval(String url) {
        String host = URI.create(url).getHost();
        if (host == null) return;
        long interval = props.getMinHostIntervalMs();
        long now = System.currentTimeMillis();
        Long last = lastAccessByHost.get(host);
        if (last != null) {
            long wait = interval - (now - last);
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
        lastAccessByHost.put(host, System.currentTimeMillis());
    }

    /** リトライ対象の一時障害（IOException/5xx/429）。 */
    static final class TransientHttpException extends RuntimeException {
        TransientHttpException(String message) { super(message); }
    }
}
