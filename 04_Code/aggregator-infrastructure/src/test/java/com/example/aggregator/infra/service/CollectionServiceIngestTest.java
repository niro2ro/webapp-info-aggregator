package com.example.aggregator.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.aggregator.domain.collect.RawItem;
import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.EventDatePrecision;
import com.example.aggregator.domain.model.SourceEntity;
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
                new UrlNormalizer(), new UrlHasher(), new EventDateKindResolver());
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
