package com.example.aggregator.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aggregator.domain.collect.ArticleContentExtractor;
import com.example.aggregator.domain.collect.RawItem;
import com.example.aggregator.domain.llm.LlmStructurer;
import com.example.aggregator.domain.llm.StructuredArticle;
import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.EventDateKind;
import com.example.aggregator.domain.model.EventDatePrecision;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.domain.rule.EventDateKindResolver;
import com.example.aggregator.domain.rule.UrlHasher;
import com.example.aggregator.domain.rule.UrlNormalizer;
import com.example.aggregator.infra.persistence.ArticleRepository;
import com.example.aggregator.infra.persistence.ArticleThemeMatchRepository;
import com.example.aggregator.infra.persistence.ThemeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 収集の「RSS→LLM フォールバック」を検証する（外部IF §1.1）。ドメインルールは本物、リポジトリと
 * LlmStructurer はダブルを使う。<b>NoOp（空）なら RSS 値のまま／構造化できたら不足項目だけ上書き</b>。
 */
class CollectionServiceFallbackTest {

    private final ArticleRepository articles = mock(ArticleRepository.class);
    private final ArticleThemeMatchRepository matches = mock(ArticleThemeMatchRepository.class);
    private final ThemeRepository themes = mock(ThemeRepository.class);
    private final ArticleContentExtractor extractor = mock(ArticleContentExtractor.class);

    private CollectionService service(LlmStructurer structurer) {
        lenient().when(extractor.extract(any())).thenReturn(Optional.empty());
        return new CollectionService(articles, matches, themes,
                new UrlNormalizer(), new UrlHasher(), new EventDateKindResolver(), structurer, extractor);
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
    @DisplayName("LLMが空(NoOp)なら実イベント日(event_date)はNULLで、RSS配信日はpublished_atに入る")
    void noOpSeparatesPublishedFromEvent() {
        when(articles.existsByUrlHash(any())).thenReturn(false);
        when(articles.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        LlmStructurer noop = input -> Optional.empty();

        java.time.Instant pub = java.time.Instant.parse("2026-09-18T00:00:00Z");
        RawItem item = new RawItem("新商品の予約開始", "https://example.com/a", pub, "本文の説明", null);
        boolean added = service(noop).ingestOne(source(), item, List.of());

        assertThat(added).isTrue();
        ArticleEntity saved = captureSaved();
        assertThat(saved.getCategory()).isEqualTo(Category.OTHER);
        assertThat(saved.getEventDate()).isNull();                 // 実イベント日は読まないと不明
        assertThat(saved.getPublishedAt()).isEqualTo(pub);         // 記事の掲載日はRSS配信日
        assertThat(saved.getEventDatePrecision()).isEqualTo(EventDatePrecision.UNKNOWN);
        assertThat(saved.getSummary()).isEqualTo("本文の説明");
    }

    @Test
    @DisplayName("LLM が構造化できたら、不足していた発生日・分類・要約を上書きする")
    void llmOverlaysMissingFields() {
        when(articles.existsByUrlHash(any())).thenReturn(false);
        when(articles.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        StructuredArticle sa = new StructuredArticle(
                "無視されるタイトル", Category.ANIME, LocalDate.of(2026, 9, 1), "9月1日",
                EventDatePrecision.EXACT, EventDateKind.BROADCAST, "東京", "LLMの自作要約");
        LlmStructurer stub = input -> Optional.of(sa);

        RawItem item = new RawItem("アニメ放送日決定", "https://example.com/b", null, "元の説明", null);
        boolean added = service(stub).ingestOne(source(), item, List.of());

        assertThat(added).isTrue();
        ArticleEntity saved = captureSaved();
        assertThat(saved.getCategory()).isEqualTo(Category.ANIME);            // OTHER → ANIME
        assertThat(saved.getEventDate()).isEqualTo(LocalDate.of(2026, 9, 1)); // 補完
        assertThat(saved.getEventDatePrecision()).isEqualTo(EventDatePrecision.EXACT);
        assertThat(saved.getEventDateText()).isEqualTo("9月1日");
        assertThat(saved.getLocation()).isEqualTo("東京");
        assertThat(saved.getSummary()).isEqualTo("LLMの自作要約");            // 要約差し替え
        assertThat(saved.getTitle()).isEqualTo("アニメ放送日決定");          // タイトルは RSS 由来を維持
    }

    @Test
    @DisplayName("url_hash 既存なら重複としてスキップ（冪等の一次判定）")
    void duplicateSkipped() {
        when(articles.existsByUrlHash(any())).thenReturn(true);
        LlmStructurer noop = input -> Optional.empty();

        RawItem item = new RawItem("既出", "https://example.com/a", null, "x", null);
        boolean added = service(noop).ingestOne(source(), item, List.of());

        assertThat(added).isFalse();
        org.mockito.Mockito.verify(articles, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("同一タイトル（別サイト＝別URL）は重複として登録しない")
    void sameTitleFromDifferentSourceSkipped() {
        when(articles.existsByUrlHash(any())).thenReturn(false);          // URLは新規
        when(articles.existsByTitleKey(any())).thenReturn(true);          // 同じタイトルが既存
        LlmStructurer noop = input -> Optional.empty();

        RawItem item = new RawItem("新作フィギュア予約開始 - dメニューニュース",
                "https://dmenu.example.com/x", null, "説明", null);
        boolean added = service(noop).ingestOne(source(), item, List.of());

        assertThat(added).isFalse();
        org.mockito.Mockito.verify(articles, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("LLM無効(isEnabled=false)なら記事本文を取得しない（無駄打ち防止）")
    void disabledStructurerSkipsBodyFetch() {
        when(articles.existsByUrlHash(any())).thenReturn(false);
        when(articles.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        // NoOp 相当: 構造化しない かつ 無効を宣言
        LlmStructurer disabled = new LlmStructurer() {
            @Override public Optional<StructuredArticle> structure(com.example.aggregator.domain.llm.ExtractedText in) {
                return Optional.empty();
            }
            @Override public boolean isEnabled() { return false; }
        };

        RawItem item = new RawItem("記事", "https://example.com/z", null, "説明", null);
        boolean added = service(disabled).ingestOne(source(), item, List.of());

        assertThat(added).isTrue();
        verify(extractor, never()).extract(any());   // 本文取得は呼ばれない
    }
}
