package com.example.aggregator.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** テーマお気に入り（TBL-FavoriteThemes / favorite_themes）。通知ON/OFF を持つ（FR-05-01/04）。 */
@Entity
@Table(name = "favorite_themes")
public class FavoriteThemeEntity {

    @EmbeddedId
    private Key key;

    @Column(name = "notify_enabled", nullable = false)
    private boolean notifyEnabled = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected FavoriteThemeEntity() {}

    public FavoriteThemeEntity(Long userId, Long themeId, boolean notifyEnabled) {
        this.key = new Key(userId, themeId);
        this.notifyEnabled = notifyEnabled;
    }

    public Key getKey() { return key; }
    public boolean isNotifyEnabled() { return notifyEnabled; }
    public void setNotifyEnabled(boolean v) { this.notifyEnabled = v; }

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "user_id", nullable = false)
        private Long userId;
        @Column(name = "theme_id", nullable = false)
        private Long themeId;

        protected Key() {}
        public Key(Long userId, Long themeId) { this.userId = userId; this.themeId = themeId; }

        public Long getThemeId() { return themeId; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(userId, k.userId) && Objects.equals(themeId, k.themeId);
        }
        @Override public int hashCode() { return Objects.hash(userId, themeId); }
    }
}
