package com.example.aggregator.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 通知送信ログ（TBL-NotificationLogs / notification_logs）。無料枠管理（当月通数 SUM）と分類別の実績に使う。
 * {@code messageCount} は消費通数＝送信先ユーザー数 × 吹き出し数（カルーセル集約で 1 に抑える・BD-IF-03-01）。
 */
@Entity
@Table(name = "notification_logs")
public class NotificationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "sent_at", nullable = false, insertable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "article_count", nullable = false)
    private int articleCount;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "status", nullable = false)
    private NotifyStatus status;

    protected NotificationLogEntity() {}

    public NotificationLogEntity(Long userId, int articleCount, int messageCount, NotifyStatus status) {
        this.userId = userId;
        this.articleCount = articleCount;
        this.messageCount = messageCount;
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public java.time.Instant getSentAt() { return sentAt; }
    public int getArticleCount() { return articleCount; }
    public int getMessageCount() { return messageCount; }
    public NotifyStatus getStatus() { return status; }
}
