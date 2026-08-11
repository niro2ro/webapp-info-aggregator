package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.ArticleEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 記事リポジトリ（Spring Data JPA）。interface を宣言するだけで実装が生成される。
 */
public interface ArticleRepository extends JpaRepository<ArticleEntity, Long>, ArticleSearchRepository {

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

    /** 指定利用者がブックマークした記事（発生日順）。※ネイティブ結果は本リポジトリの entity(ArticleEntity)へ写像。 */
    @Query(value = """
            SELECT a.* FROM articles a
            JOIN bookmarks b ON b.article_id = a.id
            WHERE b.user_id = :userId
            ORDER BY COALESCE(a.event_date, (a.created_at AT TIME ZONE 'UTC')::date) DESC, a.created_at DESC
            """, nativeQuery = true)
    List<ArticleEntity> findBookmarkedByUser(Long userId);

    /**
     * 通知バッチの抽出（BD-BATCH-N）: 指定利用者にとって<b>未通知</b>かつ<b>お気に入り対象</b>の新着記事。
     *
     * <ul>
     *   <li>未通知 … article_notifications に (user, article) 行が無い（＝二重通知防止・§5）</li>
     *   <li>お気に入り対象 … お気に入りテーマにマッチ（notify_enabled）<b>または</b>お気に入り情報源から（notify_enabled）</li>
     * </ul>
     * 発生日の新しい順。カルーセルの上限件数に切り詰めるため件数を絞る（Pageable）。
     */
    @Query(value = """
            SELECT a.* FROM articles a
            WHERE NOT EXISTS (
                    SELECT 1 FROM article_notifications an
                    WHERE an.user_id = :userId AND an.article_id = a.id)
              AND (
                    EXISTS (
                        SELECT 1 FROM article_theme_matches m
                        JOIN favorite_themes ft ON ft.theme_id = m.theme_id
                        WHERE m.article_id = a.id AND ft.user_id = :userId AND ft.notify_enabled = true)
                 OR EXISTS (
                        SELECT 1 FROM favorite_sources fs
                        WHERE fs.source_id = a.source_id AND fs.user_id = :userId AND fs.notify_enabled = true)
                  )
            ORDER BY COALESCE(a.event_date, (a.created_at AT TIME ZONE 'UTC')::date) DESC, a.created_at DESC
            """, nativeQuery = true)
    List<ArticleEntity> findUnnotifiedFavorited(Long userId, Pageable pageable);
}
