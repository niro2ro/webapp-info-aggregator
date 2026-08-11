package com.example.aggregator.infra.llm;

import com.example.aggregator.domain.llm.LlmUsage;
import com.example.aggregator.domain.model.LlmCallStatus;
import com.example.aggregator.domain.model.LlmUsageLogEntity;
import com.example.aggregator.infra.persistence.LlmUsageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * LLM コストのハードキャップ（DD-CLS-03・NFR-06）。呼び出し<b>前</b>に当月残予算を判定し、<b>後</b>に使用量を記録する。
 *
 * <p>「ユーザー数に比例してコストが増えない」構造を守るため、収集時のみ・予算内のみで LLM を使う（CLAUDE.md §5）。
 * 上限に達したら LLM を呼ばず RSS/パーサー結果で登録を続ける（収集は止めない）。
 */
@Component
public class LlmBudgetGuard {

    private static final Logger log = LoggerFactory.getLogger(LlmBudgetGuard.class);

    private final LlmUsageLogRepository usageLogs;
    private final LlmProperties props;

    public LlmBudgetGuard(LlmUsageLogRepository usageLogs, LlmProperties props) {
        this.usageLogs = usageLogs;
        this.props = props;
    }

    /** 当月累計（JST 月）が実効しきい値未満なら予算あり。 */
    public boolean hasBudget() {
        long used = usageLogs.sumCurrentMonthMicroJpy();
        boolean ok = used < props.effectiveCapMicroJpy();
        if (!ok) {
            log.warn("[LLM予算] 当月上限到達: used={}μ円 cap={}μ円 → LLM をスキップし RSS で登録",
                    used, props.effectiveCapMicroJpy());
        }
        return ok;
    }

    /** 呼び出し後の使用量を概算コスト付きで記録する。個別トランザクションで確実に残す。 */
    @Transactional
    public void recordUsage(LlmUsage usage, LlmCallStatus status, Long crawlLogId) {
        long micro = props.estimateMicroJpy(usage.inputTokens(), usage.outputTokens());
        usageLogs.save(new LlmUsageLogEntity(
                usage.model(), usage.inputTokens(), usage.outputTokens(), micro, crawlLogId, status));
    }
}
