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

    private static final String TIMELINE_KEY =
            "COALESCE(a.event_date, (a.created_at AT TIME ZONE 'UTC')::date)";

    @Override
    public List<ArticleEntity> search(ArticleQuery q) {
        StringBuilder sql = new StringBuilder("SELECT a.* FROM articles a WHERE 1=1 ");
        List<Object[]> binds = new ArrayList<>();

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
            sql.append("AND EXISTS (SELECT 1 FROM article_theme_matches m "
                    + "WHERE m.article_id = a.id AND m.theme_id = :themeId) ");
            binds.add(new Object[]{"themeId", q.themeId()});
        }
        if (q.unreadOnly() && q.userId() != null) {
            sql.append("AND NOT EXISTS (SELECT 1 FROM read_states r "
                    + "WHERE r.article_id = a.id AND r.user_id = :uid) ");
            binds.add(new Object[]{"uid", q.userId()});
        }
        if (q.sort() == ArticleQuery.Sort.RELEASE) {
            // 発売日順: 種別=発売日(1) の記事を発売日で（近い順＝昇順が上位）
            sql.append("AND a.event_date_kind = 1 ");
        }

        sql.append("ORDER BY ").append(orderBy(q.sort()));

        Query query = em.createNativeQuery(sql.toString(), ArticleEntity.class);
        for (Object[] b : binds) {
            query.setParameter((String) b[0], b[1]);
        }
        query.setMaxResults(q.limit() > 0 ? q.limit() : 50);
        @SuppressWarnings("unchecked")
        List<ArticleEntity> result = query.getResultList();
        return result;
    }

    /** ORDER BY はホワイトリスト（外部文字列を混ぜない）。 */
    private String orderBy(ArticleQuery.Sort sort) {
        return switch (sort) {
            case EVENT_DESC -> TIMELINE_KEY + " DESC, a.created_at DESC";
            case EVENT_ASC -> TIMELINE_KEY + " ASC, a.created_at ASC";
            case RELEASE -> "a.event_date ASC NULLS LAST, a.created_at DESC";
            case COLLECTED_DESC -> "a.created_at DESC";
        };
    }
}
