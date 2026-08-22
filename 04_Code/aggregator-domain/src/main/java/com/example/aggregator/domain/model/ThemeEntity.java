package com.example.aggregator.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * テーマ（TBL-Themes / themes）。収集対象カテゴリは theme_categories に @ElementCollection でマップ。
 *
 * <p>user_id は Phase 1 では関連ではなく素の Long として保持（User エンティティは Phase 3/5 で導入）。
 */
@Entity
@Table(name = "themes")
public class ThemeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "keyword", nullable = false)
    private String keyword;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // theme_categories(theme_id, category) を「カテゴリ集合」として素直に表現する。
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "theme_categories", joinColumns = @JoinColumn(name = "theme_id"))
    @Column(name = "category", nullable = false)
    private Set<Category> categories = new HashSet<>();

    protected ThemeEntity() {}

    public ThemeEntity(Long userId, String keyword, Set<Category> categories) {
        this.userId = userId;
        this.keyword = keyword;
        this.categories = new HashSet<>(categories);
        this.active = true;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Set<Category> getCategories() { return categories; }

    /** 対象カテゴリを入れ替える（@ElementCollection は中身を差し替えると save で反映される）。 */
    public void setCategories(Set<Category> categories) {
        this.categories = new HashSet<>(categories);
    }
}
