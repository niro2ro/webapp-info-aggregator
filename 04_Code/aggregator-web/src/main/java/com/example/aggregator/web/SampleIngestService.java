package com.example.aggregator.web;

import com.example.aggregator.domain.collect.RawItem;
import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.domain.model.ThemeEntity;
import com.example.aggregator.infra.persistence.SourceRepository;
import com.example.aggregator.infra.persistence.ThemeRepository;
import com.example.aggregator.infra.rss.RomeFeedFetcher;
import com.example.aggregator.infra.service.CollectionService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Phase 1 のデモ用: バンドルしたサンプル RSS を取り込む（実サイトへは接続しない）。
 * 実運用の収集（FeedFetcher で各情報源から取得）は Phase 1/2 で本実装する。UI と REST デモの共通処理。
 */
@Service
public class SampleIngestService {

    private final ThemeRepository themes;
    private final SourceRepository sources;
    private final RomeFeedFetcher feedFetcher;
    private final CollectionService collectionService;

    public SampleIngestService(ThemeRepository themes, SourceRepository sources,
                               RomeFeedFetcher feedFetcher, CollectionService collectionService) {
        this.themes = themes;
        this.sources = sources;
        this.feedFetcher = feedFetcher;
        this.collectionService = collectionService;
    }

    public void ensureSampleTheme() {
        Long userId = 2L;
        if (!themes.existsByUserIdAndKeyword(userId, "呪術廻戦")) {
            themes.save(new ThemeEntity(userId, "呪術廻戦",
                    Set.of(Category.GOODS, Category.ANIME, Category.EVENT, Category.CAPSULE_TOY)));
        }
    }

    public CollectionService.IngestResult ingestSample() {
        try {
            String xml = new String(new ClassPathResource("sample-feed.xml").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            List<RawItem> items = feedFetcher.parse(xml);
            SourceEntity source = sources.findAll().get(0);
            return collectionService.ingest(source, items);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
