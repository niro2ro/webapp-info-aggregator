package com.example.aggregator.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** 既読状態（TBL-ReadStates / read_states）。行が在る＝既読（複合PK・冪等）。 */
@Entity
@Table(name = "read_states")
public class ReadStateEntity {

    @EmbeddedId
    private Key key;

    @Column(name = "read_at", nullable = false, insertable = false, updatable = false)
    private Instant readAt;

    protected ReadStateEntity() {}

    public ReadStateEntity(Long userId, Long articleId) {
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
