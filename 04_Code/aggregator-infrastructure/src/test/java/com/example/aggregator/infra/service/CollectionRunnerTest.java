package com.example.aggregator.infra.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aggregator.domain.collect.HttpFetcher;
import com.example.aggregator.domain.collect.RawItem;
import com.example.aggregator.domain.collect.RobotsGate;
import com.example.aggregator.domain.model.CrawlLogEntity;
import com.example.aggregator.domain.model.CrawlStatus;
import com.example.aggregator.domain.model.FetchType;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.infra.persistence.CrawlLogRepository;
import com.example.aggregator.infra.persistence.SourceRepository;
import com.example.aggregator.infra.rss.RomeFeedFetcher;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 収集オーケストレーションの要点（規約ゲート・robots・障害分離・CrawlLog）を検証する。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollectionRunnerTest {

    @Mock SourceRepository sources;
    @Mock RobotsGate robotsGate;
    @Mock HttpFetcher http;
    @Mock RomeFeedFetcher feedFetcher;
    @Mock CollectionService collectionService;
    @Mock CrawlLogRepository crawlLogs;

    private CollectionRunner runner() {
        return new CollectionRunner(sources, robotsGate, http, feedFetcher, collectionService, crawlLogs);
    }

    private SourceEntity rss(String name) {
        return new SourceEntity(name, "https://" + name + ".example.com/rss", FetchType.RSS);
    }

    @Test
    @DisplayName("正常: robots許可→取得→解析→取り込み→CrawlLog=Success・last_fetched更新")
    void happyPath() {
        SourceEntity s = rss("a");
        when(sources.findByActiveTrueAndTermsReviewedTrue()).thenReturn(List.of(s));
        when(robotsGate.isAllowed(anyString())).thenReturn(true);
        when(http.get(anyString())).thenReturn("<rss/>");
        List<RawItem> items = List.of(new RawItem("t", "https://a.example.com/1", null, "d", null));
        when(feedFetcher.parse(anyString())).thenReturn(items);
        when(collectionService.ingest(any(), any())).thenReturn(new CollectionService.IngestResult(3, 2, 1));

        CollectionRunner.RunResult r = runner().run();

        assertThat(r.sources()).isEqualTo(1);
        assertThat(r.succeeded()).isEqualTo(1);
        assertThat(r.totalRegistered()).isEqualTo(2);
        verify(sources).save(s);   // last_fetched_at 更新
        ArgumentCaptor<CrawlLogEntity> cap = ArgumentCaptor.forClass(CrawlLogEntity.class);
        verify(crawlLogs).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(CrawlStatus.SUCCESS);
    }

    @Test
    @DisplayName("robots不許可: 取得せずスキップ・CrawlLog=PartialError")
    void robotsDisallowed() {
        SourceEntity s = rss("b");
        when(sources.findByActiveTrueAndTermsReviewedTrue()).thenReturn(List.of(s));
        when(robotsGate.isAllowed(anyString())).thenReturn(false);

        CollectionRunner.RunResult r = runner().run();

        assertThat(r.failed()).isEqualTo(1);
        verify(http, never()).get(anyString());
        verify(collectionService, never()).ingest(any(), any());
        ArgumentCaptor<CrawlLogEntity> cap = ArgumentCaptor.forClass(CrawlLogEntity.class);
        verify(crawlLogs).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(CrawlStatus.PARTIAL_ERROR);
    }

    @Test
    @DisplayName("障害分離: 1情報源が失敗しても他は処理され、両方のCrawlLogが残る")
    void failureIsolation() {
        SourceEntity bad = rss("bad");
        SourceEntity good = rss("good");
        when(sources.findByActiveTrueAndTermsReviewedTrue()).thenReturn(List.of(bad, good));
        when(robotsGate.isAllowed(anyString())).thenReturn(true);
        // bad は取得で例外、good は成功
        when(http.get(bad.getUrl())).thenThrow(new RuntimeException("boom"));
        when(http.get(good.getUrl())).thenReturn("<rss/>");
        when(feedFetcher.parse(anyString())).thenReturn(List.of());
        when(collectionService.ingest(any(), any())).thenReturn(new CollectionService.IngestResult(0, 0, 0));

        CollectionRunner.RunResult r = runner().run();

        assertThat(r.sources()).isEqualTo(2);
        assertThat(r.succeeded()).isEqualTo(1);
        assertThat(r.failed()).isEqualTo(1);
        verify(crawlLogs, org.mockito.Mockito.times(2)).save(any());   // 失敗分も必ず記録
    }

    @Test
    @DisplayName("未対応の取得方式(HTMLパーサー)はこの情報源だけ失敗にして継続")
    void unsupportedFetchType() {
        SourceEntity html = new SourceEntity("h", "https://h.example.com", FetchType.HTML_PARSER);
        when(sources.findByActiveTrueAndTermsReviewedTrue()).thenReturn(List.of(html));

        CollectionRunner.RunResult r = runner().run();

        assertThat(r.failed()).isEqualTo(1);
        verify(collectionService, never()).ingest(any(), any());
    }
}
