package com.example.aggregator.infra.rss;

import com.example.aggregator.domain.collect.FeedFetcher;
import com.example.aggregator.domain.collect.RawItem;
import com.rometools.rome.feed.synd.SyndCategory;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ROME による RSS/Atom 解析（FR-02-01・DD-CLS-12）。
 *
 * <p>フォーマット解釈はライブラリに任せる（「決まった形式をライブラリで構造体へ」）。ネットワーク取得は
 * {@link #fetch(String)}（実運用）、解析のみは {@link #parse(Reader, String)}（テスト/デモで
 * ネットワークなしに検証可能）に分けている。
 */
@Component
public class RomeFeedFetcher implements FeedFetcher {

    @Override
    public List<RawItem> fetch(String feedUrl) {
        try (XmlReader reader = new XmlReader(URI.create(feedUrl).toURL())) {
            return toRawItems(new SyndFeedInput().build(reader));
        } catch (Exception e) {
            throw new IllegalStateException("RSS 取得/解析に失敗: " + feedUrl, e);
        }
    }

    /** 文字列（またはReader）からの解析。単体テスト・Phase 0/1 のデモで使う（ネットワーク不要）。 */
    public List<RawItem> parse(String xml) {
        return parse(new StringReader(xml), null);
    }

    public List<RawItem> parse(Reader reader, String ignoredBaseUrl) {
        try {
            return toRawItems(new SyndFeedInput().build(reader));
        } catch (Exception e) {
            throw new IllegalStateException("RSS 解析に失敗", e);
        }
    }

    private List<RawItem> toRawItems(SyndFeed feed) {
        List<RawItem> items = new ArrayList<>();
        for (SyndEntry e : feed.getEntries()) {
            Instant published = e.getPublishedDate() != null ? e.getPublishedDate().toInstant()
                    : (e.getUpdatedDate() != null ? e.getUpdatedDate().toInstant() : null);
            String description = e.getDescription() != null ? e.getDescription().getValue() : null;
            String categoryHint = e.getCategories().stream()
                    .findFirst().map(SyndCategory::getName).orElse(null);
            items.add(new RawItem(e.getTitle(), e.getLink(), published, description, categoryHint));
        }
        return items;
    }
}
