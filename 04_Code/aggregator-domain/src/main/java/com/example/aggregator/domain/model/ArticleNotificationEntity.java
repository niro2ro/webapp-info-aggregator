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
 * 記事×利用者の通知済み記録（TBL-ArticleNotifications / article_notifications）。
 *
 * <p><b>冪等性の最終防衛線（§5）</b>: 主キー (user_id, article_id) が存在すれば「その利用者へその記事は通知済み」。
 * 同日2回目以降・別実行が同じ記事を通知しようとしても、この行の有無で二重通知を防ぐ（DB 制約で担保し
 * アプリ判定だけに頼らない）。{@code result} は Delivered / GaveUp。
 */
@Entity
@Table(name = "article_notifications")
public class ArticleNotificationEntity {

    @EmbeddedId
    private Key key;

    @Column(name = "notified_at", nullable = false, insertable = false, updatable = false)
    private Instant notifiedAt;

    @Column(name = "result", nullable = false)
    private NotifyResult result = NotifyResult.DELIVERED;

    protected ArticleNotificationEntity() {}

    public ArticleNotificationEntity(Long userId, Long articleId, NotifyResult result) {
        this.key = new Key(userId, articleId);
        this.result = result;
    }

    public Key getKey() { return key; }
    public NotifyResult getResult() { return result; }

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "user_id", nullable = false)
        private Long userId;
        @Column(name = "article_id", nullable = false)
        private Long articleId;

        protected Key() {}
        public Key(Long userId, Long articleId) { this.userId = userId; this.articleId = articleId; }

        public Long getUserId() { return userId; }
        public Long getArticleId() { return articleId; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(userId, k.userId) && Objects.equals(articleId, k.articleId);
        }
        @Override public int hashCode() { return Objects.hash(userId, articleId); }
    }
}
