package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.FavoriteThemeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** テーマお気に入りのリポジトリ。 */
public interface FavoriteThemeRepository extends JpaRepository<FavoriteThemeEntity, FavoriteThemeEntity.Key> {

    List<FavoriteThemeEntity> findByKeyUserId(Long userId);
}
