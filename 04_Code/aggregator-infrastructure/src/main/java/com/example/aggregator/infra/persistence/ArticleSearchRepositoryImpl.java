package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.ArticleEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.List;

/**
 * 動的検索の実装（FR-04-02/03）。ネイティブSQLを組み立てる。
 *
 * <p>安全対策: 値はすべて<b>バインドパラメータ</b>で渡し文字列連結しない。ORDER BY だけは列挙から
 * <b>ホワイトリスト</b>で固定式を選ぶ（外部入力を ORDER BY に流さない＝SQLインジェクション防止）。
 * 部分一致は pg_trgm の GIN インデックスが効く ILIKE を使う（gin_trgm_ops・NFR-04）。
 * 整列の COALESCE 式はタイムライン式インデックス ix_articles_timeline と同一にする。
 */
public class ArticleSearchRepositoryImpl implements ArticleSearchRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<ArticleEntity> search(ArticleQuery q) {
        StringBuilder sql = new StringBuilder("SELECT a.* FROM articles a WHERE 1=1 ");
        List<Object[]> binds = new ArrayList<>();
        appendWhere(sql, binds, q);
        sql.append("ORDER BY ").append(orderBy(q.sort()));

        Query query = em.createNativeQuery(sql.toString(), ArticleEntity.class);
        bind(query, binds);
        if (q.offset() > 0) query.setFirstResult(q.offset());   // ページング開始位置
        query.setMaxResults(q.limit() > 0 ? q.limit() : 50);
        @SuppressWarnings("unchecked")
        List<ArticleEntity> result = query.getResultList();
        return result;
    }

    @Override
    public long count(ArticleQuery q) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM articles a WHERE 1=1 ");
        List<Object[]> binds = new ArrayList<>();
        appendWhere(sql, binds, q);   // ORDER BY / LIMIT は付けない（件数だけ）
        Query query = em.createNativeQuery(sql.toString());
        bind(query, binds);
        return ((Number) query.getSingleResult()).longValue();
    }

    /** 絞り込み条件（WHERE）を組み立てる。search と count で共有し、両者の一致を保証する。 */
    private void appendWhere(StringBuilder sql, List<Object[]> binds, ArticleQuery q) {
        if (q.text() != null && !q.text().isBlank()) {
            sql.append("AND (a.title ILIKE :pat OR a.summary ILIKE :pat) ");
            binds.add(new Object[]{"pat", "%" + q.text().trim() + "%"});
        }
        if (q.category() != null) {
            sql.append("AND a.category = :cat ");
            binds.add(new Object[]{"cat", q.category().code()});
        }
        if (q.kind() != null) {
            sql.append("AND a.event_date_kind = :kind ");
            binds.add(new Object[]{"kind", q.kind().code()});
        }
        if (q.themeId() != null) {
            // 特定テーマを選択: そのテーマにマッチした記事のみ。
            sql.append("AND EXISTS (SELECT 1 FROM article_theme_matches m "
                    + "WHERE m.article_id = a.id AND m.theme_id = :themeId) ");
            binds.add(new Object[]{"themeId", q.themeId()});
        } else if (q.userId() != null) {
            // テーマ未指定: タイムラインは「ログイン利用者の有効テーマにマッチした記事のみ」に絞る（SC-02）。
            // テーマを削除すると article_theme_matches も連動削除（ON DELETE CASCADE）されるため、
            // そのテーマ由来の記事は自動的に一覧から消える。有効テーマが0件なら何も表示されない。
            sql.append("AND EXISTS (SELECT 1 FROM article_theme_matches m "
                    + "JOIN themes t ON t.id = m.theme_id "
                    + "WHERE m.article_id = a.id AND t.user_id = :uid AND t.is_active = true) ");
            binds.add(new Object[]{"uid", q.userId()});
        }
        if (q.sourceId() != null) {
            sql.append("AND a.source_id = :sid ");
            binds.add(new Object[]{"sid", q.sourceId()});
        }
        if (q.unreadOnly() && q.userId() != null) {
            sql.append("AND NOT EXISTS (SELECT 1 FROM read_states r "
                    + "WHERE r.article_id = a.id AND r.user_id = :uid) ");
            binds.add(new Object[]{"uid", q.userId()});
        }
    }

    private void bind(Query query, List<Object[]> binds) {
        for (Object[] b : binds) {
            query.setParameter((String) b[0], b[1]);
        }
    }

    /** ORDER BY はホワイトリスト（外部文字列を混ぜない）。 */
    private String orderBy(ArticleQuery.Sort sort) {
        return switch (sort) {
            // 発売日(実イベント日)。日付が無い記事は末尾へ（NULLS LAST）。
            case RELEASE_ASC -> "a.event_date ASC NULLS LAST, a.created_at DESC";    // 近い順
            case RELEASE_DESC -> "a.event_date DESC NULLS LAST, a.created_at DESC";  // 遠い順
            case PUBLISHED_DESC -> "a.published_at DESC NULLS LAST, a.created_at DESC"; // 掲載日順
            case COLLECTED_DESC -> "a.created_at DESC";                             // 収集日順
        };
    }
}
