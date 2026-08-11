package com.example.aggregator.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aggregator.domain.collect.HttpFetcher;
import com.example.aggregator.domain.collect.RawItem;
import com.example.aggregator.domain.model.FetchType;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.domain.model.ThemeEntity;
import com.example.aggregator.infra.persistence.SourceRepository;
import com.example.aggregator.infra.persistence.ThemeRepository;
import com.example.aggregator.infra.rss.RomeFeedFetcher;
import com.example.aggregator.infra.web.CollectProperties;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** テーマ検索収集: キーワードがURLエンコードされ、テーマ単位で障害分離されることを検証する。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThemeSearchCollectorTest {

    @Mock ThemeRepository themes;
    @Mock SourceRepository sources;
    @Mock HttpFetcher http;
    @Mock RomeFeedFetcher feedFetcher;
    @Mock CollectionService collectionService;

    private ThemeSearchCollector collector() {
        CollectProperties props = new CollectProperties();
        props.setSearchFeedUrlTemplate("https://example.com/rss?q={q}");
        return new ThemeSearchCollector(themes, sources, http, feedFetcher, collectionService, props);
    }

    private ThemeEntity theme(String kw) {
        return new ThemeEntity(2L, kw, Set.of());
    }

    @Test
    @DisplayName("キーワードはURLエンコードされて検索URLに埋め込まれる")
    void encodesKeyword() {
        when(sources.findFirstByName(any())).thenReturn(Optional.of(
                new SourceEntity("s", "https://news.google.com/", FetchType.RSS)));
        when(themes.findByUserIdAndActiveTrueOrderByKeyword(2L)).thenReturn(List.of(theme("夏目友人帳")));
        when(http.get(anyString())).thenReturn("<rss/>");
        when(feedFetcher.parse(anyString())).thenReturn(List.of());
        when(collectionService.ingest(any(), any())).thenReturn(new CollectionService.IngestResult(0, 0, 0));

        collector().collectForUser(2L);

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(http).get(url.capture());
        // 「夏目友人帳」のUTF-8パーセントエンコードが含まれる（生の日本語ではない）
        assertThat(url.getValue()).isEqualTo("https://example.com/rss?q=%E5%A4%8F%E7%9B%AE%E5%8F%8B%E4%BA%BA%E5%B8%B3");
    }

    @Test
    @DisplayName("あるテーマの取得が失敗しても他テーマは処理される（障害分離）")
    void isolatesPerTheme() {
        when(sources.findFirstByName(any())).thenReturn(Optional.of(
                new SourceEntity("s", "https://news.google.com/", FetchType.RSS)));
        when(themes.findByUserIdAndActiveTrueOrderByKeyword(2L))
                .thenReturn(List.of(theme("A"), theme("B")));
        when(http.get(org.mockito.ArgumentMatchers.contains("q=A"))).thenThrow(new RuntimeException("boom"));
        when(http.get(org.mockito.ArgumentMatchers.contains("q=B"))).thenReturn("<rss/>");
        when(feedFetcher.parse(anyString())).thenReturn(List.of());
        when(collectionService.ingest(any(), any())).thenReturn(new CollectionService.IngestResult(1, 1, 0));

        ThemeSearchCollector.RunResult r = collector().collectForUser(2L);

        assertThat(r.themes()).isEqualTo(2);
        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.totalRegistered()).isEqualTo(1);   // B の分は登録された
    }

    @Test
    @DisplayName("検索用ソースが無ければ作成する（active=falseで通常ループ対象外）")
    void createsSearchSourceIfMissing() {
        when(sources.findFirstByName(any())).thenReturn(Optional.empty());
        when(sources.save(any())).thenAnswer(i -> i.getArgument(0));
        when(themes.findByUserIdAndActiveTrueOrderByKeyword(2L)).thenReturn(List.of());

        collector().collectForUser(2L);

        ArgumentCaptor<SourceEntity> cap = ArgumentCaptor.forClass(SourceEntity.class);
        verify(sources).save(cap.capture());
        assertThat(cap.getValue().isActive()).isFalse();
    }
}
