package com.example.aggregator.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * 記事×テーマ マッチ（TBL-ArticleThemeMatches / article_theme_matches）。
 * 複合PK (article_id, theme_id) が重複突合を防ぐ（冪等・FR-02-10）。
 */
@Entity
@Table(name = "article_theme_matches")
public class ArticleThemeMatchEntity {

    @EmbeddedId
    private Key key;

    @Column(name = "matched_at", nullable = false, insertable = false, updatable = false)
    private Instant matchedAt;

    protected ArticleThemeMatchEntity() {}

    public ArticleThemeMatchEntity(Long articleId, Long themeId) {
        this.key = new Key(articleId, themeId);
    }

    public Key getKey() { return key; }

    /** 複合主キー。@Embeddable ＋ Serializable が JPA の要件。 */
    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "article_id", nullable = false)
        private Long articleId;
        @Column(name = "theme_id", nullable = false)
        private Long themeId;

        protected Key() {}
        public Key(Long articleId, Long themeId) { this.articleId = articleId; this.themeId = themeId; }

        public Long getArticleId() { return articleId; }
        public Long getThemeId() { return themeId; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(articleId, k.articleId) && Objects.equals(themeId, k.themeId);
        }
        @Override public int hashCode() { return Objects.hash(articleId, themeId); }
    }
}
