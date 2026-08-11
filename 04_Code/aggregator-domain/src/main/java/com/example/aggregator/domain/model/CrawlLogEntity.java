package com.example.aggregator.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 収集ログ（TBL-CrawlLogs / crawl_logs）。情報源単位の実行結果を記録する（障害分離・NFR-10）。 */
@Entity
@Table(name = "crawl_logs")
public class CrawlLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "new_item_count", nullable = false)
    private int newItemCount;

    @Column(name = "status", nullable = false)
    private CrawlStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    protected CrawlLogEntity() {}

    public CrawlLogEntity(Long sourceId, Instant startedAt) {
        this.sourceId = sourceId;
        this.startedAt = startedAt;
        this.status = CrawlStatus.SUCCESS;
    }

    public void finish(CrawlStatus status, int itemCount, int newItemCount, String errorMessage) {
        this.status = status;
        this.itemCount = itemCount;
        this.newItemCount = newItemCount;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSourceId() { return sourceId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getItemCount() { return itemCount; }
    public int getNewItemCount() { return newItemCount; }
    public CrawlStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
}
