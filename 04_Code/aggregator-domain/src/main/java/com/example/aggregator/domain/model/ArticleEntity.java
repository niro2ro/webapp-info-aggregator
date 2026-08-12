package com.example.aggregator.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 記事（TBL-Articles / articles）。<b>自作要約＋メタ＋元URL</b>のみを保持し、本文・公式画像は保存しない（§9）。
 * url_hash の UNIQUE 制約が冪等の一次担保（FR-02-08）。
 */
@Entity
@Table(name = "articles")
public class ArticleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "category", nullable = false)
    private Category category;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "event_date_text")
    private String eventDateText;

    @Column(name = "event_date_precision", nullable = false)
    private EventDatePrecision eventDatePrecision = EventDatePrecision.UNKNOWN;

    @Column(name = "event_date_kind", nullable = false)
    private EventDateKind eventDateKind = EventDateKind.OTHER;

    @Column(name = "location")
    private String location;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "url_hash", nullable = false, unique = true)
    private String urlHash;

    @Column(name = "summary")
    private String summary;

    @Column(name = "group_key")
    private String groupKey;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ArticleEntity() {}

    private ArticleEntity(Builder b) {
        this.sourceId = b.sourceId;
        this.title = b.title;
        this.category = b.category;
        this.eventDate = b.eventDate;
        this.publishedAt = b.publishedAt;
        this.eventDateText = b.eventDateText;
        this.eventDatePrecision = b.eventDatePrecision;
        this.eventDateKind = b.eventDateKind;
        this.location = b.location;
        this.url = b.url;
        this.urlHash = b.urlHash;
        this.summary = b.summary;
        this.groupKey = b.groupKey;
    }

    /**
     * 既存記事に実イベント日（発売日等）を後から反映する（再解析・LLM）。日付が取れたときだけ更新する。
     * 種別はカテゴリ/原文ルールの結果を優先し、ルールで OTHER のときだけ LLM の種別を採用する（外部IF §2.2）。
     */
    public void enrichEventDate(LocalDate eventDate, EventDatePrecision precision,
                               EventDateKind kind, String eventDateText, String location) {
        if (eventDate == null) return;
        this.eventDate = eventDate;
        if (precision != null) this.eventDatePrecision = precision;
        if (eventDateText != null) this.eventDateText = eventDateText;
        if (location != null) this.location = location;
        if (kind != null && this.eventDateKind == EventDateKind.OTHER) this.eventDateKind = kind;
    }

    public Long getId() { return id; }
    public Long getSourceId() { return sourceId; }
    public String getTitle() { return title; }
    public Category getCategory() { return category; }
    public LocalDate getEventDate() { return eventDate; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getEventDateText() { return eventDateText; }
    public EventDatePrecision getEventDatePrecision() { return eventDatePrecision; }
    public EventDateKind getEventDateKind() { return eventDateKind; }
    public String getLocation() { return location; }
    public String getUrl() { return url; }
    public String getUrlHash() { return urlHash; }
    public String getSummary() { return summary; }
    public Instant getCreatedAt() { return createdAt; }

    public static Builder builder() { return new Builder(); }

    /** record 的な生成の代わりに Builder を用意（必須項目が多く、可読性のため）。 */
    public static final class Builder {
        private Long sourceId;
        private String title;
        private Category category;
        private LocalDate eventDate;
        private Instant publishedAt;
        private String eventDateText;
        private EventDatePrecision eventDatePrecision = EventDatePrecision.UNKNOWN;
        private EventDateKind eventDateKind = EventDateKind.OTHER;
        private String location;
        private String url;
        private String urlHash;
        private String summary;
        private String groupKey;

        public Builder sourceId(Long v) { this.sourceId = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public Builder category(Category v) { this.category = v; return this; }
        public Builder eventDate(LocalDate v) { this.eventDate = v; return this; }
        public Builder publishedAt(Instant v) { this.publishedAt = v; return this; }
        public Builder eventDateText(String v) { this.eventDateText = v; return this; }
        public Builder eventDatePrecision(EventDatePrecision v) { this.eventDatePrecision = v; return this; }
        public Builder eventDateKind(EventDateKind v) { this.eventDateKind = v; return this; }
        public Builder location(String v) { this.location = v; return this; }
        public Builder url(String v) { this.url = v; return this; }
        public Builder urlHash(String v) { this.urlHash = v; return this; }
        public Builder summary(String v) { this.summary = v; return this; }
        public Builder groupKey(String v) { this.groupKey = v; return this; }
        public ArticleEntity build() { return new ArticleEntity(this); }
    }
}
