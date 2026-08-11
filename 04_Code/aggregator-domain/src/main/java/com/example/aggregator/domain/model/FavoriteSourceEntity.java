package com.example.aggregator.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** 情報源お気に入り（TBL-FavoriteSources / favorite_sources）。通知ON/OFF を持つ（FR-05-02/04）。 */
@Entity
@Table(name = "favorite_sources")
public class FavoriteSourceEntity {

    @EmbeddedId
    private Key key;

    @Column(name = "notify_enabled", nullable = false)
    private boolean notifyEnabled = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected FavoriteSourceEntity() {}

    public FavoriteSourceEntity(Long userId, Long sourceId, boolean notifyEnabled) {
        this.key = new Key(userId, sourceId);
        this.notifyEnabled = notifyEnabled;
    }

    public Key getKey() { return key; }
    public boolean isNotifyEnabled() { return notifyEnabled; }
    public void setNotifyEnabled(boolean v) { this.notifyEnabled = v; }

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "user_id", nullable = false)
        private Long userId;
        @Column(name = "source_id", nullable = false)
        private Long sourceId;

        protected Key() {}
        public Key(Long userId, Long sourceId) { this.userId = userId; this.sourceId = sourceId; }

        public Long getSourceId() { return sourceId; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(userId, k.userId) && Objects.equals(sourceId, k.sourceId);
        }
        @Override public int hashCode() { return Objects.hash(userId, sourceId); }
    }
}
