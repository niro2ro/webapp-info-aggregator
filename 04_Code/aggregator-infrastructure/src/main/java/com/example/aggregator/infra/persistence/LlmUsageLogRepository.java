package com.example.aggregator.infra.persistence;

import com.example.aggregator.domain.model.LlmUsageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** LLM 使用ログ。当月（JST 月）コストの合計を予算ガードが参照する（FR-06-06・NFR-06）。 */
public interface LlmUsageLogRepository extends JpaRepository<LlmUsageLogEntity, Long> {

    /**
     * 当月（Asia/Tokyo の暦月）の概算コスト合計（マイクロ円）。
     * called_at は timestamptz(UTC)。JST に変換してから月初と比較する（NFR-08・DD-DAO）。
     * まだ1件も無ければ COALESCE で 0 を返す。
     */
    @Query(value = """
            SELECT COALESCE(SUM(est_cost_micro_jpy), 0)
            FROM llm_usage_logs
            WHERE (called_at AT TIME ZONE 'Asia/Tokyo')
                  >= date_trunc('month', (now() AT TIME ZONE 'Asia/Tokyo'))
            """, nativeQuery = true)
    long sumCurrentMonthMicroJpy();

    /**
     * 当月（JST 月）の集計を1行返す（各要素: [呼び出し回数, 入力トークン計, 出力トークン計]）。
     * ネイティブの複数列は {@code List<Object[]>} で受ける（{@code long[]} だと1要素しか返らず配列範囲外になる）。
     */
    @Query(value = """
            SELECT COALESCE(COUNT(*),0), COALESCE(SUM(input_tokens),0), COALESCE(SUM(output_tokens),0)
            FROM llm_usage_logs
            WHERE (called_at AT TIME ZONE 'Asia/Tokyo')
                  >= date_trunc('month', (now() AT TIME ZONE 'Asia/Tokyo'))
            """, nativeQuery = true)
    java.util.List<Object[]> currentMonthAggregate();
}
