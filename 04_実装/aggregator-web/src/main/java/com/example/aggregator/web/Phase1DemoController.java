package com.example.aggregator.web;

import com.example.aggregator.domain.collect.RawItem;
import com.example.aggregator.domain.model.ArticleEntity;
import com.example.aggregator.domain.model.Category;
import com.example.aggregator.domain.model.SourceEntity;
import com.example.aggregator.domain.model.ThemeEntity;
import com.example.aggregator.infra.persistence.ArticleRepository;
import com.example.aggregator.infra.persistence.SourceRepository;
import com.example.aggregator.infra.persistence.ThemeRepository;
import com.example.aggregator.infra.rss.RomeFeedFetcher;
import com.example.aggregator.infra.service.CollectionService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 1 の疎通デモ（ネットワーク不要）。バンドルしたサンプル RSS を ROME で解析し、収集サービスで
 * 取り込み、タイムライン（発生日順）を返す。Phase 1 の完成条件「1テーマ分が日付順に並ぶ」を外形確認する。
 * ここは暫定で、正式な画面（Vaadin）に置き換わる。
 */
@RestController
@RequestMapping("/demo")
public class Phase1DemoController {

    private final ThemeRepository themes;
    private final SourceRepository sources;
    private final ArticleRepository articles;
    private final RomeFeedFetcher feedFetcher;
    private final CollectionService collectionService;

    public Phase1DemoController(ThemeRepository themes, SourceRepository sources, ArticleRepository articles,
                               RomeFeedFetcher feedFetcher, CollectionService collectionService) {
        this.themes = themes;
        this.sources = sources;
        this.articles = articles;
        this.feedFetcher = feedFetcher;
        this.collectionService = collectionService;
    }

    /** テーマ「呪術廻戦」を（無ければ）登録する。 */
    @PostMapping("/seed-theme")
    public Object seedTheme() {
        Long userId = 2L; // シードの一般利用者
        if (!themes.existsByUserIdAndKeyword(userId, "呪術廻戦")) {
            themes.save(new ThemeEntity(userId, "呪術廻戦",
                    Set.of(Category.GOODS, Category.ANIME, Category.EVENT, Category.CAPSULE_TOY)));
        }
        return themes.findByActiveTrue().stream().map(ThemeEntity::getKeyword).toList();
    }

    /** サンプル RSS を解析して収集（冪等：複数回叩いても重複登録されない）。 */
    @PostMapping("/collect")
    public CollectionService.IngestResult collect() throws IOException {
        String xml = new String(new ClassPathResource("sample-feed.xml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        List<RawItem> items = feedFetcher.parse(xml);
        SourceEntity source = sources.findAll().get(0); // デモ用に既存の情報源へ紐づける
        return collectionService.ingest(source, items);
    }

    /** タイムライン（発生日順・降順）。テーマ指定があればそのテーマにマッチした記事のみ。 */
    @GetMapping("/timeline")
    public List<Row> timeline() {
        return articles.findTimeline(PageRequest.of(0, 20)).stream().map(Row::from).toList();
    }

    @GetMapping("/timeline/theme")
    public List<Row> timelineByTheme() {
        Long themeId = themes.findByActiveTrue().stream()
                .filter(t -> t.getKeyword().equals("呪術廻戦")).map(ThemeEntity::getId).findFirst().orElse(-1L);
        return articles.findTimelineByTheme(themeId, PageRequest.of(0, 20)).stream().map(Row::from).toList();
    }

    /** 一覧表示用の軽量DTO（エンティティを画面に晒さない・DD-CLS-31）。 */
    public record Row(String dateLabel, String category, String title, String url) {
        static Row from(ArticleEntity a) {
            String kind = a.getEventDateKind().label();
            String date = a.getEventDate() != null ? a.getEventDate().toString() : "(日付不明)";
            String label = (kind.isEmpty() ? "" : kind + " ") + date;
            return new Row(label, a.getCategory().name(), a.getTitle(), a.getUrl());
        }
    }
}
