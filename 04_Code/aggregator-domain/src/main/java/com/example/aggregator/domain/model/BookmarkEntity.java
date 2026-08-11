package com.example.aggregator.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** ブックマーク（TBL-Bookmarks / bookmarks・後で見る）。通知対象ではない。データパージ除外対象（NFR-05）。 */
@Entity
@Table(name = "bookmarks")
public class BookmarkEntity {

    @EmbeddedId
    private Key key;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected BookmarkEntity() {}

    public BookmarkEntity(Long userId, Long articleId) {
        this.key = new Key(userId, articleId);
    }

    public Key getKey() { return key; }

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "user_id", nullable = false)
        private Long userId;
        @Column(name = "article_id", nullable = false)
        private Long articleId;

        protected Key() {}
        public Key(Long userId, Long articleId) { this.userId = userId; this.articleId = articleId; }

        public Long getArticleId() { return articleId; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(userId, k.userId) && Objects.equals(articleId, k.articleId);
        }
        @Override public int hashCode() { return Objects.hash(userId, articleId); }
    }
}
