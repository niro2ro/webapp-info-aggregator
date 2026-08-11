package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.SourceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 情報源リポジトリ。収集対象は「有効 かつ 規約確認済」のみ（規約ゲート・FR-02-12）。 */
public interface SourceRepository extends JpaRepository<SourceEntity, Long> {

    List<SourceEntity> findByActiveTrueAndTermsReviewedTrue();

    /** 管理用の情報源（テーマ検索の格納先など）を名前で1件取得。 */
    Optional<SourceEntity> findFirstByName(String name);
}
