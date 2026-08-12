package com.example.aggregator.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aggregator.domain.collect.ArticleContentExtractor;
import com.example.aggregator.domain.llm.ExtractedText;
import com.example.aggregator.domain.llm.LlmStructurer;
import com.example.aggregator.domain.llm.StructuredArticle;
import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.EventDateKind;
import com.example.aggregator.domain.model.EventDatePrecision;
import com.example.aggregator.infra.persistence.ArticleRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 既存記事の再解析（発売日の穴埋め）の要点を検証する。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArticleReanalyzeServiceTest {

    @Mock ArticleRepository articles;
    @Mock ArticleContentExtractor extractor;
    @Mock LlmStructurer llm;

    private ArticleReanalyzeService service() {
        return new ArticleReanalyzeService(articles, extractor, llm);
    }

    private ArticleEntity articleWithoutDate() {
        return ArticleEntity.builder().sourceId(1L).title("新作フィギュア").category(Category.GOODS)
                .url("https://ex.com/1").urlHash("h1").eventDateKind(EventDateKind.OTHER).build();
    }

    @Test
    @DisplayName("LLM無効なら何もしない（リポジトリも触らない）")
    void disabledDoesNothing() {
        when(llm.isEnabled()).thenReturn(false);
        ArticleReanalyzeService.Result r = service().reanalyzeMissingEventDates(30);
        assertThat(r.llmEnabled()).isFalse();
        assertThat(r.scanned()).isZero();
        verify(articles, never()).findByEventDateIsNullOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("LLMが発売日を返したら event_date を更新して保存")
    void fillsEventDate() {
        when(llm.isEnabled()).thenReturn(true);
        ArticleEntity a = articleWithoutDate();
        when(articles.findByEventDateIsNullOrderByCreatedAtDesc(any())).thenReturn(List.of(a));
        when(extractor.extract(any())).thenReturn(Optional.of("……9月20日発売予定……"));
        StructuredArticle sa = new StructuredArticle("x", Category.GOODS, LocalDate.of(2026, 9, 20),
                "9月20日", EventDatePrecision.EXACT, EventDateKind.RELEASE, null, "要約");
        when(llm.structure(any(ExtractedText.class))).thenReturn(Optional.of(sa));

        ArticleReanalyzeService.Result r = service().reanalyzeMissingEventDates(30);

        assertThat(r.updated()).isEqualTo(1);
        assertThat(a.getEventDate()).isEqualTo(LocalDate.of(2026, 9, 20));   // DBの値が埋まる
        assertThat(a.getEventDateKind()).isEqualTo(EventDateKind.RELEASE);   // OTHER→LLM種別を採用
        verify(articles).save(a);
    }

    @Test
    @DisplayName("LLMが発売日を返さなければ更新しない")
    void noDateNoUpdate() {
        when(llm.isEnabled()).thenReturn(true);
        ArticleEntity a = articleWithoutDate();
        when(articles.findByEventDateIsNullOrderByCreatedAtDesc(any())).thenReturn(List.of(a));
        when(extractor.extract(any())).thenReturn(Optional.empty());
        when(llm.structure(any(ExtractedText.class))).thenReturn(Optional.empty());

        ArticleReanalyzeService.Result r = service().reanalyzeMissingEventDates(30);

        assertThat(r.updated()).isZero();
        assertThat(a.getEventDate()).isNull();
        verify(articles, never()).save(any());
    }
}
