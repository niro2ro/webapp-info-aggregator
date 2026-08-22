package com.example.aggregator.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aggregator.domain.collect.RawItem;
import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.EventDatePrecision;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.domain.model.ThemeEntity;
import com.example.aggregator.domain.rule.EventDateExtractor;
import com.example.aggregator.domain.rule.EventDateKindResolver;
import com.example.aggregator.domain.rule.UrlHasher;
import com.example.aggregator.domain.rule.UrlNormalizer;
import com.example.aggregator.infra.persistence.ArticleRepository;
import com.example.aggregator.infra.persistence.ArticleThemeMatchRepository;
import com.example.aggregator.infra.persistence.ThemeRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 収集の取り込み（RSSのみ・LLMを呼ばない）を検証する。ドメインルールは本物、リポジトリはダブル。
 * <b>掲載日(published_at)は RSS 配信日から確実に入り、発売日(event_date)は収集時は NULL</b>（発売日は
 * 「発売日順」選択時や再解析ボタンでオンデマンドに LLM が補完する）。冪等（URL/タイトル）も確認する。
 */
class CollectionServiceIngestTest {

    private final ArticleRepository articles = mock(ArticleRepository.class);
    private final ArticleThemeMatchRepository matches = mock(ArticleThemeMatchRepository.class);
    private final ThemeRepository themes = mock(ThemeRepository.class);

    private CollectionService service() {
        return new CollectionService(articles, matches, themes,
                new UrlNormalizer(), new UrlHasher(), new EventDateKindResolver(), new EventDateExtractor());
    }

    private SourceEntity source() {
        SourceEntity s = mock(SourceEntity.class);
        when(s.getId()).thenReturn(1L);
        return s;
    }

    private ArticleEntity captureSaved() {
        ArgumentCaptor<ArticleEntity> captor = ArgumentCaptor.forClass(ArticleEntity.class);
        org.mockito.Mockito.verify(articles).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("収集はRSSのみ: event_dateはNULL、RSS配信日はpublished_atに入る（LLMは呼ばない）")
    void rssOnlySeparatesPublishedFromEvent() {
        when(articles.existsByUrlHash(any())).thenReturn(false);
        when(articles.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        java.time.Instant pub = java.time.Instant.parse("2026-09-18T00:00:00Z");
        RawItem item = new RawItem("新商品の予約開始", "https://example.com/a", pub, "本文の説明", null);
        boolean added = service().ingestOne(source(), item, List.of());

        assertThat(added).isTrue();
        ArticleEntity saved = captureSaved();
        assertThat(saved.getCategory()).isEqualTo(Category.OTHER);
        assertThat(saved.getEventDate()).isNull();                 // 発売日は収集時は不明（LLMで後補完）
        assertThat(saved.getPublishedAt()).isEqualTo(pub);         // 掲載日はRSS配信日で確実
        assertThat(saved.getEventDatePrecision()).isEqualTo(EventDatePrecision.UNKNOWN);
        assertThat(saved.getSummary()).isEqualTo("本文の説明");
    }

    @Test
    @DisplayName("タイトルに日付があればルールで発売日(event_date)を収集時に埋める（LLM不使用）")
    void ruleFillsEventDateAtIngest() {
        when(articles.existsByUrlHash(any())).thenReturn(false);
        when(articles.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        RawItem item = new RawItem("夏目友人帳 一番くじ 2026年9月18日発売",
                "https://example.com/c", null, "グッズ情報", null);
        boolean added = service().ingestOne(source(), item, List.of());

        assertThat(added).isTrue();
        ArticleEntity saved = captureSaved();
        assertThat(saved.getEventDate()).isEqualTo(java.time.LocalDate.of(2026, 9, 18));   // ルールで抽出
        assertThat(saved.getEventDatePrecision()).isEqualTo(EventDatePrecision.EXACT);
        assertThat(saved.getEventDateText()).contains("2026年9月18日");
    }

    // --- テーマ突合（カテゴリ絞り込みの挙動）: 各カテゴリで確認 ---

    private ThemeEntity theme(String keyword, Category... cats) {
        return new ThemeEntity(2L, keyword, java.util.Set.of(cats));
    }

    @Test
    @DisplayName("カテゴリ不明(その他)の記事は、テーマのカテゴリに関係なくキーワード一致で突合される")
    void unknownCategoryMatchesAnyCategoryTheme() {
        when(articles.existsByUrlHash(any())).thenReturn(false);
        when(articles.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        // テーマ検索由来を想定: categoryHint=null → 記事カテゴリは OTHER になる
        RawItem item = new RawItem("セガ 新作アーケード筐体が稼働", "https://ex.com/arcade", null, "ゲーセン情報", null);

        // ゲームセンターだけを対象にしたテーマ（その他は含めない）でも、OTHER記事はキーワードで拾える
        service().ingestOne(source(), item, List.of(theme("セガ", Category.ARCADE)));

        verify(matches).save(any());   // 突合が作られる（＝タイムラインに出る）
    }

    @Test
    @DisplayName("分類が確定している記事は、テーマのカテゴリが合わなければ突合しない（絞り込みは効く）")
    void knownCategoryMismatchDoesNotMatch() {
        when(articles.existsByUrlHash(any())).thenReturn(false);
        when(articles.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        // categoryHint=「ゲーム」→ 記事カテゴリ GAME（確定）
        RawItem item = new RawItem("フィギュア付き限定ゲーム", "https://ex.com/g", null, "説明", "ゲーム");

        // グッズだけのテーマ → GAME は対象外なので突合しない
        service().ingestOne(source(), item, List.of(theme("フィギュア", Category.GOODS)));

        verify(matches, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("ゲームセンターのヒント(プライズ)があれば ARCADE 判定＝ゲームセンターのテーマに突合")
    void arcadeHintMatchesArcadeTheme() {
        when(articles.existsByUrlHash(any())).thenReturn(false);
        when(articles.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        RawItem item = new RawItem("新プライズ景品が登場", "https://ex.com/p", null, "説明", "プライズ");

        service().ingestOne(source(), item, List.of(theme("プライズ", Category.ARCADE)));

        ArticleEntity saved = captureSaved();
        assertThat(saved.getCategory()).isEqualTo(Category.ARCADE);   // ゲームセンター(ARCADE)に分類
        verify(matches).save(any());
    }

    @Test
    @DisplayName("空カテゴリのテーマは全カテゴリ対象（キーワード一致だけで突合）")
    void emptyCategoryThemeMatchesAny() {
        when(articles.existsByUrlHash(any())).thenReturn(false);
        when(articles.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        RawItem item = new RawItem("アニメ 第2期 制作決定", "https://ex.com/a", null, "説明", "アニメ");

        service().ingestOne(source(), item, List.of(theme("第2期")));   // カテゴリ指定なし

        verify(matches).save(any());
    }

    @Test
    @DisplayName("url_hash 既存なら重複としてスキップ（冪等の一次判定）")
    void duplicateSkipped() {
        when(articles.existsByUrlHash(any())).thenReturn(true);

        RawItem item = new RawItem("既出", "https://example.com/a", null, "x", null);
        boolean added = service().ingestOne(source(), item, List.of());

        assertThat(added).isFalse();
        org.mockito.Mockito.verify(articles, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("同一タイトル（別サイト＝別URL）は重複として登録しない")
    void sameTitleFromDifferentSourceSkipped() {
        when(articles.existsByUrlHash(any())).thenReturn(false);          // URLは新規
        when(articles.existsByTitleKey(any())).thenReturn(true);          // 同じタイトルが既存

        RawItem item = new RawItem("新作フィギュア予約開始 - dメニューニュース",
                "https://dmenu.example.com/x", null, "説明", null);
        boolean added = service().ingestOne(source(), item, List.of());

        assertThat(added).isFalse();
        org.mockito.Mockito.verify(articles, org.mockito.Mockito.never()).saveAndFlush(any());
    }
}
