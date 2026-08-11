package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.ArticleNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 記事×利用者の通知済み記録。主キー (user_id, article_id) の存在が二重通知防止の要（§5）。
 * 抽出（未通知の記事）は {@link ArticleRepository#findUnnotifiedFavorited} 側に置く（戻り値が ArticleEntity のため）。
 */
public interface ArticleNotificationRepository
        extends JpaRepository<ArticleNotificationEntity, ArticleNotificationEntity.Key> {

    boolean existsByKey(ArticleNotificationEntity.Key key);
}
