package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.SourceEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 情報源リポジトリ。収集対象は「有効 かつ 規約確認済」のみ（規約ゲート・FR-02-12）。 */
public interface SourceRepository extends JpaRepository<SourceEntity, Long> {

    List<SourceEntity> findByActiveTrueAndTermsReviewedTrue();
}
