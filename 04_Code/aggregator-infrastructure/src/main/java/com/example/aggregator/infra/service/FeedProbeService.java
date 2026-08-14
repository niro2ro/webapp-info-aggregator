package com.example.aggregator.infra.service;

import com.example.aggregator.domain.collect.HttpFetcher;
import com.example.aggregator.domain.collect.RawItem;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.infra.rss.RomeFeedFetcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * RSS取得の診断（確認専用）。任意のフィードURLを実際に取得・解析し、<b>DBには一切保存せず</b>、
 * 「取得できたか・件数・先頭数件のタイトル/日付」だけを返す。実サイトのRSSが正しく取れるかを、
 * 規約確認や情報源登録より前に切り分けるための道具（FR-06相当の運用支援）。
 *
 * <p><b>本番収集と同じ経路を使う</b>: 取得は {@link HttpFetcher}（連絡先入りUA・タイムアウト・リトライ・
 * 同一ホスト間隔などの収集マナーを内包）、解析は {@link RomeFeedFetcher}（ROME）で行う。＝ここで取れれば、
 * 収集バッチ／テーマ検索収集と同じ仕組みで取れることの確認になる。例外は投げず {@link ProbeResult} に畳む。
 */
@Service
public class FeedProbeService {

    private static final Logger log = LoggerFactory.getLogger(FeedProbeService.class);
    /** 表示する先頭サンプル件数（多すぎても確認用途では不要）。画面の説明文からも参照する。 */
    public static final int SAMPLE_LIMIT = 5;

    private final HttpFetcher http;
    private final RomeFeedFetcher feedFetcher;

    public FeedProbeService(HttpFetcher http, RomeFeedFetcher feedFetcher) {
        this.http = http;
        this.feedFetcher = feedFetcher;
    }

    /** 診断1件の結果。{@code ok=false} のとき {@code error} に失敗理由（取得/解析どちらか）を入れる。 */
    public record ProbeResult(boolean ok, int count, List<ProbeItem> samples, String error) {
        public static ProbeResult failure(String error) {
            return new ProbeResult(false, 0, List.of(), error);
        }
    }

    /** サンプル1件（保存はしないので記事エンティティは作らない・タイトルと配信日とURLのみ）。 */
    public record ProbeItem(String title, Instant publishedAt, String url) {}

    /** 情報源1件ぶんの診断結果（一括テスト用・どの情報源のどのURLか分かるよう名前とURLを添える）。 */
    public record NamedProbe(String name, String url, ProbeResult result) {}

    /**
     * 情報源マスタの各URLを1件ずつ個別に取得・解析して結果を返す（保存しない）。1件が失敗しても
     * 残りは続行する（{@link #probe(String)} が例外を畳むため、ここでのループは単純でよい）。
     * 取得は {@link HttpFetcher} が同一ホスト間隔などのマナーを守るため、複数URLの逐次実行でも失礼にならない。
     */
    public List<NamedProbe> probeSources(List<SourceEntity> sources) {
        List<NamedProbe> out = new ArrayList<>();
        for (SourceEntity s : sources) {
            out.add(new NamedProbe(s.getName(), s.getUrl(), probe(s.getUrl())));
        }
        return out;
    }

    /**
     * 指定URLを取得・解析して結果だけ返す（保存しない）。URL空・取得失敗・解析失敗はすべて
     * {@code ok=false} の結果として返し、画面側でそのまま表示できるようにする。
     */
    public ProbeResult probe(String url) {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) {
            return ProbeResult.failure("URLを入力してください。");
        }
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            return ProbeResult.failure("URLは http:// または https:// で始めてください。");
        }
        String xml;
        try {
            xml = http.get(u);                 // 取得（マナー付き）。4xx/5xx・接続断はここで例外
        } catch (RuntimeException e) {
            log.info("[RSS診断] 取得失敗 url={} : {}", u, e.toString());
            return ProbeResult.failure("取得に失敗しました（接続・タイムアウト・4xx/5xxなど）: " + rootMessage(e));
        }
        List<RawItem> items;
        try {
            items = feedFetcher.parse(xml);    // 解析（ROME）。RSS/AtomでなければここでNG
        } catch (RuntimeException e) {
            log.info("[RSS診断] 解析失敗 url={} : {}", u, e.toString());
            return ProbeResult.failure("取得はできましたが、RSS/Atomとして解析できませんでした"
                    + "（このURLはRSSフィードではない可能性）: " + rootMessage(e));
        }
        List<ProbeItem> samples = new ArrayList<>();
        for (RawItem it : items) {
            if (samples.size() >= SAMPLE_LIMIT) break;
            samples.add(new ProbeItem(it.title(), it.publishedAt(), it.url()));
        }
        log.info("[RSS診断] 成功 url={} 件数={}", u, items.size());
        return new ProbeResult(true, items.size(), samples, null);
    }

    /** 例外チェーンの末端メッセージを取り出す（画面表示用に簡潔化）。 */
    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }
}
