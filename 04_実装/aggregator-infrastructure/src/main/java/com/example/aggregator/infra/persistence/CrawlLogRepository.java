package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.CrawlLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** 収集ログのリポジトリ。 */
public interface CrawlLogRepository extends JpaRepository<CrawlLogEntity, Long> {
}
