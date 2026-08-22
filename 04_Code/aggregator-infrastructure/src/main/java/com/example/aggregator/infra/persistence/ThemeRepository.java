package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.ThemeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** テーマリポジトリ。収集突合の対象は有効テーマ（is_active=true）。 */
public interface ThemeRepository extends JpaRepository<ThemeEntity, Long> {

    List<ThemeEntity> findByActiveTrue();

    /** 指定利用者の有効テーマ（キーワード順）。タイムラインのテーマ絞り込み・テーマ検索収集に使う。 */
    List<ThemeEntity> findByUserIdAndActiveTrueOrderByKeyword(Long userId);

    /** 指定利用者の全テーマ（有効/停止を問わず・キーワード順）。テーマ管理画面の一覧に使う。 */
    List<ThemeEntity> findByUserIdOrderByKeyword(Long userId);

    boolean existsByUserIdAndKeyword(Long userId, String keyword);
}
