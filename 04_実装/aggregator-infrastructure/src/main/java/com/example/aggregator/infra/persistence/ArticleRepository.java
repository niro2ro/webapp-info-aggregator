package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.ArticleEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 記事リポジトリ（Spring Data JPA）。interface を宣言するだけで実装が生成される。
 */
public interface ArticleRepository extends JpaRepository<ArticleEntity, Long> {

    /** 冪等の一次判定（url_hash 既存チェック・FR-02-08）。 */
    boolean existsByUrlHash(String urlHash);

    /**
     * タイムライン（発生日順・降順）。整列キーは式インデックス ix_articles_timeline と同一式にする。
     * timestamptz::date は IMMUTABLE でないため UTC 固定で date 化（実装Phase0で確定）。
     */
    @Query(value = """
            SELECT * FROM articles a
            ORDER BY COALESCE(a.event_date, (a.created_at AT TIME ZONE 'UTC')::date) DESC, a.created_at DESC
            """, nativeQuery = true)
    List<ArticleEntity> findTimeline(Pageable pageable);

    /** 指定テーマにマッチした記事のみ（発生日順）。 */
    @Query(value = """
            SELECT a.* FROM articles a
            JOIN article_theme_matches m ON m.article_id = a.id
            WHERE m.theme_id = :themeId
            ORDER BY COALESCE(a.event_date, (a.created_at AT TIME ZONE 'UTC')::date) DESC, a.created_at DESC
            """, nativeQuery = true)
    List<ArticleEntity> findTimelineByTheme(Long themeId, Pageable pageable);
}
