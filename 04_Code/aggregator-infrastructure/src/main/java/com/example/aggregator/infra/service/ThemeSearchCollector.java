package com.example.aggregator.infra.service;

import com.example.aggregator.domain.collect.HttpFetcher;
import com.example.aggregator.domain.collect.RawItem;
import com.example.aggregator.domain.model.FetchType;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.domain.model.ThemeEntity;
import com.example.aggregator.infra.persistence.SourceRepository;
import com.example.aggregator.infra.persistence.ThemeRepository;
import com.example.aggregator.infra.rss.RomeFeedFetcher;
import com.example.aggregator.infra.web.CollectProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * テーマ検索収集。<b>登録テーマのキーワードで「検索RSS」（既定 Googleニュース）を引き、その結果を取り込む</b>。
 * 固定サイトRSS（{@link CollectionRunner}）が「サイトが出した記事を後で突合」なのに対し、こちらは
 * 「テーマ語で最近の記事を探しに行く」用途。取り込みは既存の {@link CollectionService}（正規化・冪等・要約・
 * テーマ突合）に委譲するため、集めた記事は自動でそのテーマに紐づく。
 *
 * <p>マナー: 連絡先入り User-Agent・タイムアウト・同一ホスト間隔は {@link HttpFetcher} が担保する。検索RSSは
 * 機械可読フィードのため robots クロールゲートは通さない（サービスの<b>利用規約は各自で確認</b>・設定で差し替え可）。
 * 取得できる期間は概ね「直近の新着」で、数か月前まで遡れるとは限らない（無料の検索RSSの制約）。
 */
@Service
public class ThemeSearchCollector {

    private static final Logger log = LoggerFactory.getLogger(ThemeSearchCollector.class);
    /** 検索結果記事の格納先となる管理用情報源の名前（由来判定に画面からも参照するため公開）。 */
    public static final String SEARCH_SOURCE_NAME = "テーマ検索（Googleニュース）";

    private final ThemeRepository themes;
    private final SourceRepository sources;
    private final HttpFetcher http;
    private final RomeFeedFetcher feedFetcher;
    private final CollectionService collectionService;
    private final CollectProperties props;

    public ThemeSearchCollector(ThemeRepository themes, SourceRepository sources, HttpFetcher http,
                                RomeFeedFetcher feedFetcher, CollectionService collectionService,
                                CollectProperties props) {
        this.themes = themes;
        this.sources = sources;
        this.http = http;
        this.feedFetcher = feedFetcher;
        this.collectionService = collectionService;
        this.props = props;
    }

    public record RunResult(int themes, int totalFetched, int totalRegistered, int failed) {}

    /** 指定利用者の有効テーマすべてについて、検索RSSから収集する。テーマ単位で障害分離する。 */
    public RunResult collectForUser(Long userId) {
        SourceEntity source = ensureSearchSource();
        List<ThemeEntity> targets = themes.findByUserIdAndActiveTrueOrderByKeyword(userId);
        int fetched = 0, registered = 0, failed = 0;
        for (ThemeEntity theme : targets) {
            try {
                String url = buildUrl(theme.getKeyword());
                String xml = http.get(url);
                List<RawItem> items = capItems(feedFetcher.parse(xml));
                CollectionService.IngestResult r = collectionService.ingest(source, items);
                fetched += r.total();
                registered += r.registered();
                log.info("[テーマ検索] '{}' 取得={} 新規={}", theme.getKeyword(), r.total(), r.registered());
            } catch (RuntimeException e) {
                failed++;
                log.warn("[テーマ検索] '{}' で失敗。次のテーマへ: {}", theme.getKeyword(), e.toString());
            }
        }
        return new RunResult(targets.size(), fetched, registered, failed);
    }

    /**
     * 1テーマあたりの処理件数を上限で切る（広いキーワードで検索RSSが大量に返ると、1件ごとの本文取得＋LLM補完が
     * 直列で走って収集が長時間化するため）。検索RSSは概ね新着順なので上位Nで十分。上限0以下は無制限。
     */
    private List<RawItem> capItems(List<RawItem> items) {
        int cap = props.getSearchMaxItemsPerTheme();
        if (cap <= 0 || items.size() <= cap) return items;
        return items.subList(0, cap);
    }

    /** 検索キーワードをテンプレートに埋め込む（URLエンコード）。 */
    private String buildUrl(String keyword) {
        String q = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        return props.getSearchFeedUrlTemplate().replace("{q}", q);
    }

    /** 検索結果の格納先の情報源を用意（無ければ作成）。通常の情報源ループには乗せない（active=false）。 */
    @Transactional
    public SourceEntity ensureSearchSource() {
        return sources.findFirstByName(SEARCH_SOURCE_NAME).orElseGet(() -> {
            SourceEntity s = new SourceEntity(SEARCH_SOURCE_NAME, "https://news.google.com/", FetchType.RSS);
            s.setActive(false);   // 検索専用。固定サイト巡回(CollectionRunner)の対象にしない
            return sources.save(s);
        });
    }
}
