package com.example.aggregator.infra.service;

import com.example.aggregator.domain.collect.ArticleContentExtractor;
import com.example.aggregator.domain.collect.RawItem;
import com.example.aggregator.domain.llm.ExtractedText;
import com.example.aggregator.domain.llm.LlmStructurer;
import com.example.aggregator.domain.llm.StructuredArticle;
import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.ArticleThemeMatchEntity;
import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.EventDateKind;
import com.example.aggregator.domain.model.EventDatePrecision;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.domain.model.ThemeEntity;
import com.example.aggregator.domain.rule.EventDateKindResolver;
import com.example.aggregator.domain.rule.UrlHasher;
import com.example.aggregator.domain.rule.UrlNormalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
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
    private final LlmStructurer llmStructurer;
    private final ArticleContentExtractor contentExtractor;

    public CollectionService(ArticleRepository articles,
                             ArticleThemeMatchRepository matches,
                             ThemeRepository themes,
                             UrlNormalizer urlNormalizer,
                             UrlHasher urlHasher,
                             EventDateKindResolver kindResolver,
                             LlmStructurer llmStructurer,
                             ArticleContentExtractor contentExtractor) {
        this.articles = articles;
        this.matches = matches;
        this.themes = themes;
        this.urlNormalizer = urlNormalizer;
        this.contentExtractor = contentExtractor;
        this.urlHasher = urlHasher;
        this.kindResolver = kindResolver;
        this.llmStructurer = llmStructurer;
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

        // 一次判定（無駄な処理を省く）。最終防衛線は UNIQUE 制約（下の catch）。
        if (articles.existsByUrlHash(hash)) {
            return false;
        }

        // --- ① RSS 由来の値（既定・LLM を使わない一次情報） ---
        Category category = guessCategory(item.categoryHint());
        String summary = toSummary(item.description());
        String textForKind = item.title() + " " + (item.description() == null ? "" : item.description());
        EventDateKind kind = kindResolver.resolve(category, textForKind);
        String eventDateText = null;
        String location = null;

        // 記事の掲載日（配信日）は published_at に保持する（＝「記事発生日」）。
        // event_date は「実イベント日（発売日/開催日/放送日）」専用にし、RSS 配信日を代入しない。
        // 実イベント日は記事本文の理解が要るため、LLM/パーサーが埋める（無ければ NULL のまま）。
        Instant publishedAt = item.publishedAt();
        LocalDate eventDate = null;
        EventDatePrecision precision = EventDatePrecision.UNKNOWN;

        // --- ② LLM フォールバック（外部IF §1.1 ③）: RSS で「発生日不明」または「分類不明」のときだけ ---
        // LLM が無効（NoOp・キー未設定）なら本文取得もLLM呼び出しも行わず①の値のまま進む＝軽量・無課金。
        // 予算判定・使用量記録は実装側（LLM 境界）に閉じている（NFR-06）。
        if ((eventDate == null || category == Category.OTHER) && llmStructurer.isEnabled()) {
            // 記事本文を取得して LLM に渡す（外部IF §2.2「本文抽出テキスト」）。robots/間隔は抽出器が担保。
            // 取得できなければ RSS 説明文にフォールバック。本文は保存しない（§9）。
            String extractText = contentExtractor.extract(item.url())
                    .orElseGet(() -> item.description() == null ? item.title() : item.description());
            Optional<StructuredArticle> enriched =
                    llmStructurer.structure(new ExtractedText(item.title(), item.url(), extractText));
            if (enriched.isPresent()) {
                StructuredArticle sa = enriched.get();
                if (category == Category.OTHER && sa.category() != null) category = sa.category();
                if (sa.eventDate() != null) {
                    eventDate = sa.eventDate();
                    precision = sa.eventDatePrecision() != null ? sa.eventDatePrecision() : EventDatePrecision.EXACT;
                } else if (sa.eventDatePrecision() != null) {
                    precision = sa.eventDatePrecision();
                }
                if (sa.eventDateText() != null) eventDateText = sa.eventDateText();
                // 種別はカテゴリ/原文ルールを優先し、ルールで OTHER のときだけ LLM 出力を採用（外部IF §2.2）。
                if (kind == EventDateKind.OTHER && sa.eventDateKind() != null) kind = sa.eventDateKind();
                if (sa.location() != null) location = sa.location();
                if (sa.summary() != null && !sa.summary().isBlank()) summary = sa.summary();
            }
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
