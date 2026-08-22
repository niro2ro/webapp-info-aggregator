package com.example.aggregator.infra.service;

import com.example.aggregator.domain.collect.RawItem;
import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.ArticleThemeMatchEntity;
import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.EventDateKind;
import com.example.aggregator.domain.model.EventDatePrecision;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.domain.model.ThemeEntity;
import com.example.aggregator.domain.rule.EventDateExtractor;
import com.example.aggregator.domain.rule.EventDateKindResolver;
import com.example.aggregator.domain.rule.TitleKey;
import com.example.aggregator.domain.rule.UrlHasher;
import com.example.aggregator.domain.rule.UrlNormalizer;
import java.time.Instant;
import java.time.LocalDate;
import com.example.aggregator.infra.persistence.ArticleRepository;
import com.example.aggregator.infra.persistence.ArticleThemeMatchRepository;
import com.example.aggregator.infra.persistence.ThemeRepository;
import java.util.List;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 収集の中核（DD-CLS-01・DD-SEQ-01）。取得済みの {@link RawItem} を受け取り、
 * URL 正規化→ハッシュ→冪等判定→種別決定→登録→テーマ突合 を行う（RSS 取得や robots ゲートは
 * 呼び出し側／別コンポーネントが担当する）。依存はコンストラクタ注入（DD-DI-01）。
 *
 * <p><b>収集は RSS のみ・LLM は呼ばない</b>。取得時は掲載日(published_at)まで（RSSで確実に取れる）を登録し、
 * 発売日(event_date)は未設定(NULL)のままにする。発売日の抽出はタイムラインで「発売日順」を選んだ時や
 * 管理画面の再解析ボタンで <b>オンデマンドに</b>{@code ArticleReanalyzeService} が LLM で行う（収集を軽く保つ）。
 */
@Service
public class CollectionService {

    private static final Logger log = LoggerFactory.getLogger(CollectionService.class);
    private static final int SUMMARY_MAX = 140;

    private final ArticleRepository articles;
    private final ArticleThemeMatchRepository matches;
    private final ThemeRepository themes;
    private final UrlNormalizer urlNormalizer;
    private final UrlHasher urlHasher;
    private final EventDateKindResolver kindResolver;
    private final EventDateExtractor eventDateExtractor;

    public CollectionService(ArticleRepository articles,
                             ArticleThemeMatchRepository matches,
                             ThemeRepository themes,
                             UrlNormalizer urlNormalizer,
                             UrlHasher urlHasher,
                             EventDateKindResolver kindResolver,
                             EventDateExtractor eventDateExtractor) {
        this.articles = articles;
        this.matches = matches;
        this.themes = themes;
        this.urlNormalizer = urlNormalizer;
        this.urlHasher = urlHasher;
        this.kindResolver = kindResolver;
        this.eventDateExtractor = eventDateExtractor;
    }

    public record IngestResult(int total, int registered, int duplicated) {}

    /**
     * 1情報源ぶんの取り込み。記事ごとに独立して処理し、UNIQUE 制約違反（重複）は正常系として握る。
     * トランザクションは記事単位に短く保つため、ここではまとめず {@link #ingestOne} を都度呼ぶ。
     */
    public IngestResult ingest(SourceEntity source, List<RawItem> items) {
        List<ThemeEntity> activeThemes = themes.findByActiveTrue();
        int registered = 0;
        int duplicated = 0;
        for (RawItem item : items) {
            if (item.url() == null || item.title() == null) continue;
            boolean added = ingestOne(source, item, activeThemes);
            if (added) registered++; else duplicated++;
        }
        log.info("[収集] source={} 取得={} 新規={} 重複={}", source.getName(), items.size(), registered, duplicated);
        return new IngestResult(items.size(), registered, duplicated);
    }

    /** 記事1件を取り込む。新規登録できたら true、重複なら false。 */
    @Transactional
    public boolean ingestOne(SourceEntity source, RawItem item, List<ThemeEntity> activeThemes) {
        String normalized = urlNormalizer.normalize(item.url());
        String hash = urlHasher.hash(normalized);
        String titleKey = TitleKey.of(item.title());

        // 一次判定（無駄な処理を省く）。最終防衛線は UNIQUE 制約（下の catch）。
        // ① 同一URL（正規化）は重複。② 同一タイトル（別サイトの同じ記事）は1件に集約（FR-02-09）。
        if (articles.existsByUrlHash(hash)) {
            return false;
        }
        if (!titleKey.isBlank() && articles.existsByTitleKey(titleKey)) {
            return false;
        }

        // --- RSS 由来の値のみで登録（収集では LLM を呼ばない・軽量） ---
        Category category = guessCategory(item.categoryHint());
        String summary = toSummary(item.description());
        String textForKind = item.title() + " " + (item.description() == null ? "" : item.description());
        EventDateKind kind = kindResolver.resolve(category, textForKind);

        // 記事の掲載日（配信日）は published_at に保持する（＝「記事発生日」・RSSで確実に取れる）。
        Instant publishedAt = item.publishedAt();

        // 発売日(event_date)は、まず<b>ルールベース</b>でタイトル＋説明文から日付表現を拾う（LLM不使用・無料・即時）。
        // 拾えた記事は収集時点で発売日が埋まり「発売日順」が LLM 無しで並ぶ。拾えなければ NULL のままで、
        // 曖昧なものだけ「発売日順」選択時などに ArticleReanalyzeService がオンデマンドに LLM で補完する（C案）。
        LocalDate eventDate = null;
        EventDatePrecision precision = EventDatePrecision.UNKNOWN;
        String eventDateText = null;
        String location = null;
        String dateSource = item.title() + " " + (item.description() == null ? "" : item.description());
        var extracted = eventDateExtractor.extract(dateSource, publishedAt);
        if (extracted.isPresent()) {
            eventDate = extracted.get().date();
            precision = extracted.get().precision();
            eventDateText = extracted.get().text();
        }

        ArticleEntity article = ArticleEntity.builder()
                .sourceId(source.getId())
                .title(item.title())
                .category(category)
                .eventDate(eventDate)
                .publishedAt(publishedAt)
                .eventDateText(eventDateText)
                .eventDatePrecision(precision)
                .eventDateKind(kind)
                .location(location)
                .url(item.url())
                .urlHash(hash)
                .titleKey(titleKey)
                .summary(summary)
                .build();

        try {
            ArticleEntity saved = articles.saveAndFlush(article); // ここで UNIQUE 違反が飛ぶ
            matchThemes(saved, activeThemes);
            return true;
        } catch (DataIntegrityViolationException e) {
            // 別実行が同時に入れた等。重複扱い（冪等の最終防衛線・DD-DAO-05）。
            return false;
        }
    }

    private void matchThemes(ArticleEntity article, List<ThemeEntity> activeThemes) {
        String haystack = (article.getTitle() + " " + (article.getSummary() == null ? "" : article.getSummary()));
        for (ThemeEntity theme : activeThemes) {
            boolean categoryOk = theme.getCategories().isEmpty()
                    || theme.getCategories().contains(article.getCategory());
            if (categoryOk && haystack.contains(theme.getKeyword())) {
                matches.save(new ArticleThemeMatchEntity(article.getId(), theme.getId()));
            }
        }
    }

    private Category guessCategory(String hint) {
        if (hint == null) return Category.OTHER;
        String h = hint;
        if (h.contains("グッズ")) return Category.GOODS;
        if (h.contains("アニメ")) return Category.ANIME;
        if (h.contains("漫画") || h.contains("マンガ")) return Category.MANGA;
        if (h.contains("イベント")) return Category.EVENT;
        if (h.contains("ゲームセンター") || h.contains("プライズ")) return Category.ARCADE;
        if (h.contains("ゲーム")) return Category.GAME;
        if (h.contains("カプセル") || h.contains("ガチャ")) return Category.CAPSULE_TOY;
        return Category.OTHER;
    }

    /** 自作要約（本文転載しない・§9）。RSS の説明文からタグを除去し短く整える。 */
    private String toSummary(String description) {
        if (description == null || description.isBlank()) return null;
        String text = Jsoup.parse(description).text().trim();
        return text.length() <= SUMMARY_MAX ? text : text.substring(0, SUMMARY_MAX) + "…";
    }
}
