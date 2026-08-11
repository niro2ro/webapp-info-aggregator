package com.example.aggregator.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * LLM 使用実績（TBL-LlmUsageLogs / llm_usage_logs）。当月コスト集計とハードキャップ判定に使う（NFR-06）。
 *
 * <p>コストは <b>マイクロ円</b>（円 × 100万）で保存する。理由: 1トークンあたりの単価が「円未満」の微小値で、
 * 浮動小数で積み上げると丸め誤差が出る。整数（bigint）で持てば加算・集計が誤差なく行える（会計系と同じ発想）。
 */
@Entity
@Table(name = "llm_usage_logs")
public class LlmUsageLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "called_at", nullable = false, insertable = false, updatable = false)
    private Instant calledAt;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "est_cost_micro_jpy", nullable = false)
    private long estCostMicroJpy;

    @Column(name = "crawl_log_id")
    private Long crawlLogId;

    @Column(name = "status", nullable = false)
    private LlmCallStatus status;

    protected LlmUsageLogEntity() {}

    public LlmUsageLogEntity(String model, int inputTokens, int outputTokens,
                             long estCostMicroJpy, Long crawlLogId, LlmCallStatus status) {
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.estCostMicroJpy = estCostMicroJpy;
        this.crawlLogId = crawlLogId;
        this.status = status;
    }

    public Long getId() { return id; }
    public Instant getCalledAt() { return calledAt; }
    public String getModel() { return model; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public long getEstCostMicroJpy() { return estCostMicroJpy; }
    public LlmCallStatus getStatus() { return status; }
}
