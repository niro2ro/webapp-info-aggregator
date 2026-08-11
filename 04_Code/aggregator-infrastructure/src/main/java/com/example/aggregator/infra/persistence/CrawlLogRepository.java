package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.CrawlLogEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 収集ログのリポジトリ。 */
public interface CrawlLogRepository extends JpaRepository<CrawlLogEntity, Long> {

    /** 実行ログ画面用（新しい順・直近100件）。 */
    List<CrawlLogEntity> findTop100ByOrderByStartedAtDesc();
}
