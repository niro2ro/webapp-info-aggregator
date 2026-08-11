package com.example.aggregator.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 利用者（TBL-Users / users）。通知先（{@code lineUserId}）と通知可否・最終通知時刻を持つ。
 * PIN ハッシュ等の管理系は Phase 5 で使用（本 Phase では通知に必要な項目のみ扱う）。
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "role", nullable = false)
    private UserRole role = UserRole.USER;

    @Column(name = "admin_pin_hash")
    private String adminPinHash;

    @Column(name = "line_user_id")
    private String lineUserId;

    @Column(name = "notify_enabled", nullable = false)
    private boolean notifyEnabled = true;

    @Column(name = "last_notified_at")
    private Instant lastNotifiedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected UserEntity() {}

    public UserEntity(String displayName) {
        this.displayName = displayName;
    }

    /** 通知バッチが送信成功時に呼ぶ。最終通知時刻を UTC で更新（NFR-08）。 */
    public void markNotified(Instant at) { this.lastNotifiedAt = at; }

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public UserRole getRole() { return role; }
    public String getLineUserId() { return lineUserId; }
    public void setLineUserId(String lineUserId) { this.lineUserId = lineUserId; }
    public boolean isNotifyEnabled() { return notifyEnabled; }
    public void setNotifyEnabled(boolean notifyEnabled) { this.notifyEnabled = notifyEnabled; }
    public Instant getLastNotifiedAt() { return lastNotifiedAt; }
}
