package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.ThemeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** テーマリポジトリ。収集突合の対象は有効テーマ（is_active=true）。 */
public interface ThemeRepository extends JpaRepository<ThemeEntity, Long> {

    List<ThemeEntity> findByActiveTrue();

    boolean existsByUserIdAndKeyword(Long userId, String keyword);
}
