package com.example.aggregator.infra.service;

import com.example.aggregator.infra.llm.LlmProperties;
import com.example.aggregator.infra.persistence.LlmUsageLogRepository;
import org.springframework.stereotype.Component;

/**
 * 当月のLLM利用状況とコスト（DD-CLS-09・SC-07・FR-06-06）。マイクロ円で積み上げた実績を円に換算し、
 * ハードキャップ（月500円・NFR-06）に対する残予算・到達状態を提供する（表示用に集約）。
 */
@Component
public class CostService {

    private final LlmUsageLogRepository usageLogs;
    private final LlmProperties props;

    public CostService(LlmUsageLogRepository usageLogs, LlmProperties props) {
        this.usageLogs = usageLogs;
        this.props = props;
    }

    /** 当月サマリ（円は概算）。callCount=呼び出し回数、budgetJpy=上限、capReached=実効しきい値到達。 */
    public record CostSummary(long callCount, long inputTokens, long outputTokens,
                              long costYen, int budgetJpy, long remainingYen, boolean capReached) {}

    public CostSummary currentMonth() {
        long micro = usageLogs.sumCurrentMonthMicroJpy();
        java.util.List<Object[]> rows = usageLogs.currentMonthAggregate();
        Object[] agg = rows.isEmpty() ? new Object[]{0L, 0L, 0L} : rows.get(0); // [count, inputTokens, outputTokens]
        long costYen = micro / 1_000_000L;               // マイクロ円→円（表示用に切り捨て）
        long remainingYen = Math.max(0, props.getMonthlyBudgetJpy() - costYen);
        boolean capReached = micro >= props.effectiveCapMicroJpy();
        return new CostSummary(num(agg[0]), num(agg[1]), num(agg[2]), costYen,
                props.getMonthlyBudgetJpy(), remainingYen, capReached);
    }

    /** DB の集計値（BigInteger/Long など）を long に安全変換。 */
    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
