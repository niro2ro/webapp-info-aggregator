package com.example.aggregator.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aggregator.domain.llm.LlmUsage;
import com.example.aggregator.domain.model.LlmCallStatus;
import com.example.aggregator.domain.model.LlmUsageLogEntity;
import com.example.aggregator.infra.persistence.LlmUsageLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ハードキャップ（NFR-06）のしきい値判定と使用量記録（マイクロ円）を検証する。 */
@ExtendWith(MockitoExtension.class)
class LlmBudgetGuardTest {

    @Mock LlmUsageLogRepository usageLogs;

    /** 既定: 月500円・マージン0.9 → 実効しきい値 = 450,000,000 マイクロ円。 */
    private LlmBudgetGuard guard() {
        return new LlmBudgetGuard(usageLogs, new LlmProperties());
    }

    @Test
    @DisplayName("当月累計が実効しきい値未満なら予算あり")
    void hasBudgetWhenUnderCap() {
        when(usageLogs.sumCurrentMonthMicroJpy()).thenReturn(449_999_999L);
        assertThat(guard().hasBudget()).isTrue();
    }

    @Test
    @DisplayName("実効しきい値に達したら予算なし（LLM を呼ばない）")
    void noBudgetWhenAtCap() {
        when(usageLogs.sumCurrentMonthMicroJpy()).thenReturn(450_000_000L);
        assertThat(guard().hasBudget()).isFalse();
    }

    @Test
    @DisplayName("使用量から概算コスト（マイクロ円）を計算して記録する")
    void recordUsageComputesCost() {
        // 入力1000×450 + 出力200×2250 = 450,000 + 450,000 = 900,000 マイクロ円
        guard().recordUsage(new LlmUsage("claude-sonnet-5", 1000, 200), LlmCallStatus.SUCCESS, null);

        ArgumentCaptor<LlmUsageLogEntity> captor = ArgumentCaptor.forClass(LlmUsageLogEntity.class);
        verify(usageLogs).save(captor.capture());
        assertThat(captor.getValue().getEstCostMicroJpy()).isEqualTo(900_000L);
        assertThat(captor.getValue().getStatus()).isEqualTo(LlmCallStatus.SUCCESS);
    }
}
