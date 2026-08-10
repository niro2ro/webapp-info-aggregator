package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.FavoriteSourceEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 情報源お気に入りのリポジトリ。 */
public interface FavoriteSourceRepository extends JpaRepository<FavoriteSourceEntity, FavoriteSourceEntity.Key> {

    List<FavoriteSourceEntity> findByKeyUserId(Long userId);
}
