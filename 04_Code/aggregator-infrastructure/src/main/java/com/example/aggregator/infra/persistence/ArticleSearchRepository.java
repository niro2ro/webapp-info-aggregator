package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.ArticleEntity;
import java.util.List;

/**
 * 動的な絞り込み検索のためのカスタムリポジトリ断片（Spring Data の fragment）。
 * 実装は {@code ArticleSearchRepositoryImpl}（命名規約で自動結合される）。
 */
public interface ArticleSearchRepository {
    List<ArticleEntity> search(ArticleQuery query);

    /** 同じ絞り込み条件に一致する総件数（ページ数の計算に使う。並び順・ページングは無視）。 */
    long count(ArticleQuery query);
}
