package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.ArticleThemeMatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** 記事×テーマ マッチのリポジトリ（複合PK）。 */
public interface ArticleThemeMatchRepository
        extends JpaRepository<ArticleThemeMatchEntity, ArticleThemeMatchEntity.Key> {
}
